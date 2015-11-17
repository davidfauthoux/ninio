package com.davfx.ninio.http.util;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.http.HttpMethod;
import com.davfx.ninio.http.HttpQueryPath;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.util.annotations.DefaultValue;
import com.davfx.ninio.http.util.annotations.Header;
import com.davfx.ninio.http.util.annotations.Path;
import com.davfx.ninio.http.util.annotations.PathParameter;
import com.davfx.ninio.http.util.annotations.QueryParameter;
import com.davfx.ninio.http.util.annotations.Route;

public final class AnnotatedHttpService implements AutoCloseable, Closeable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AnnotatedHttpService.class);
	
	private static final class MethodParameter {
		public static enum From {
			QUERY, PATH, HEADER
		}
		public final String name;
		public final From from;
		public MethodParameter(String name, From from) {
			this.name = name;
			this.from = from;
		}
	}
	
	private static final class PathComponent {
		public final String name;
		public final int variableIndex;
		private PathComponent(String name, int variableIndex) {
			this.name = name;
			this.variableIndex = variableIndex;
		}
		
		public static PathComponent of(String p, int variableIndex) {
			if ((p.length() >= 2) && (p.charAt(0) == '{') && (p.charAt(p.length() - 1) == '}')) {
				return new PathComponent(p.substring(1, p.length() - 1), variableIndex);
			}
			return new PathComponent(p, -1);
		}
	}

	public final HttpService service = new HttpService();

	public AnnotatedHttpService() {
	}
	
	public AnnotatedHttpService start(int port) {
		service.start(port);
		return this;
	}
	
	@Override
	public void close() {
		service.close();
	}
	
	public AnnotatedHttpService register(Class<? extends HttpController> clazz) {
		LOGGER.debug("Service class: {}", clazz);
		Path controller = (Path) clazz.getAnnotation(Path.class);
		HttpQueryPath pathPrefix = (controller == null) ? null : HttpQueryPath.of(controller.value());
		List<PathComponent> pathComponents = new LinkedList<>();
		int pathVariableIndex = 0;
		if (pathPrefix != null) {
			for (String p : pathPrefix.path) {
				pathComponents.add(PathComponent.of(p, pathVariableIndex));
				pathVariableIndex++;
			}
		}
		for (final Method method : clazz.getMethods()) {
            Route route = (Route) method.getAnnotation(Route.class);
			if (route != null) {
				final HttpMethod routeMethod = route.method();
				
				HttpQueryPath pathSuffix = route.path().isEmpty() ? null : HttpQueryPath.of(route.path());
				
				final List<PathComponent> routePathComponents = new LinkedList<>();
				routePathComponents.addAll(pathComponents);
				if (pathSuffix != null) {
					int routePathVariableIndex = pathVariableIndex;
					for (String p : pathSuffix.path) {
						routePathComponents.add(PathComponent.of(p, routePathVariableIndex));
						routePathVariableIndex++;
					}
				}
				
				final Object object;
				try {
					object = clazz.newInstance();
				} catch (Exception e) {
					LOGGER.warn("Could not create object from class {}", clazz, e);
					continue;
				}
				
				Annotation[][] parameterAnnotations = method.getParameterAnnotations();
				Class<?>[] parameterTypes = method.getParameterTypes();
				final MethodParameter[] parameters = new MethodParameter[parameterAnnotations.length];
				final String[] defaultValues = new String[parameterAnnotations.length];
				int requestParameterIndexToSet = -1;
				int postParameterIndexToSet = -1;
				int resultParameterIndexToSet = -1;
				for (int i = 0; i < parameterAnnotations.length; i++) {
					if (parameterTypes[i] == HttpRequest.class) {
						requestParameterIndexToSet = i;
						parameters[i] = null;
						continue;
					}
					
					if (parameterTypes[i] == HttpPost.class) {
						postParameterIndexToSet = i;
						parameters[i] = null;
						continue;
					}

					if (parameterTypes[i] == HttpServiceResult.class) {
						resultParameterIndexToSet = i;
						parameters[i] = null;
						continue;
					}

					Annotation[] an = parameterAnnotations[i];
					MethodParameter p = null;
					String defaultValue = null;
					for (Annotation a : an) {
						if (a.annotationType() == DefaultValue.class) {
							defaultValue = ((DefaultValue) a).value();
						}
						MethodParameter q = null;
						if (a.annotationType() == QueryParameter.class) {
							q = new MethodParameter(((QueryParameter) a).value(), MethodParameter.From.QUERY);
						}
						if (a.annotationType() == PathParameter.class) {
							q = new MethodParameter(((PathParameter) a).value(), MethodParameter.From.PATH);
						}
						if (a.annotationType() == Header.class) {
							q = new MethodParameter(((Header) a).value(), MethodParameter.From.HEADER);
						}
						if (q != null) {
							if (p != null) {
								LOGGER.warn("Multiple annotations clash: {} / {}", p, q);
							}
							p = q;
						}
					}
					parameters[i] = p;
					defaultValues[i] = defaultValue;
				}
				
				final int requestParameterIndex = requestParameterIndexToSet;
				final int postParameterIndex = postParameterIndexToSet;
				final int resultParameterIndex = resultParameterIndexToSet;

				final Map<String, Integer> pathComponentNameToIndex = new HashMap<>();
				int pathComponentIndex = 0;
				for (PathComponent pathComponent : routePathComponents) {
					pathComponentNameToIndex.put(pathComponent.name, pathComponentIndex);
					pathComponentIndex++;
				}
				
				HttpServiceHandler handler = new HttpServiceHandler() {
					@Override
					public void handle(HttpRequest request, HttpPost post, HttpServiceResult result) throws IOException {
						
						Object[] args = new Object[parameters.length];
						for (int i = 0; i < parameters.length; i++) {
							if (i == requestParameterIndex) {
								args[i] = request;
								continue;
							}
							if (i == postParameterIndex) {
								args[i] = post;
								continue;
							}
							if (i == resultParameterIndex) {
								args[i] = result;
								continue;
							}
							
							MethodParameter p = parameters[i];
							String defaultValue = defaultValues[i];
							if (p == null) {
								args[i] = defaultValue;
							} else {
								String v;
								switch (p.from) {
								case QUERY: {
									Iterator<String> it = request.path.parameters.get(p.name).iterator();
									if (!it.hasNext()) {
										v = defaultValue;
									} else {
										v = it.next();
									}
									break;
								}
								case PATH: {
									Integer index = pathComponentNameToIndex.get(p.name);
									if (index == null) {
										v = defaultValue;
									} else {
										v = request.path.path.path.get(index);
									}
									break;
								}
								case HEADER: {
									Iterator<String> it = request.headers.get(p.name).iterator();
									if (!it.hasNext()) {
										v = defaultValue;
									} else {
										v = it.next();
									}
									break;
								}
								default:
									v = defaultValue;
									break;
								}
								args[i] = v;
							}
						}

						try {
							method.invoke(object, args);
						} catch (Exception e) {
							e.printStackTrace();
							throw new IOException(e);
						}
					}
				};
				
				//TODO Order register by most specific query path
				service.register(new HttpRequestFilter() {
					@Override
					public boolean accept(HttpRequest request) {
						if (request.method != routeMethod) {
							return false;
						}
						HttpQueryPath p = request.path.path;
						Iterator<String> i = p.path.iterator();
						Iterator<PathComponent> j = routePathComponents.iterator();
						while (i.hasNext()) {
							if (!j.hasNext()) {
								return true;
							}
							String s = i.next();
							PathComponent t = j.next();
							if (t.variableIndex >= 0) {
								continue;
							}
							if (!s.equals(t.name)) {
								return false;
							}
						}
						return !j.hasNext();
					}
				}, handler);
			}
		}
		
		return this;
	}
}
