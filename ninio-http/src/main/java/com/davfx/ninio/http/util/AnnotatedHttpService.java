package com.davfx.ninio.http.util;

import java.io.File;
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

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.http.HttpMethod;
import com.davfx.ninio.http.HttpPath;
import com.davfx.ninio.http.HttpQueryPath;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpRequestFilter;
import com.davfx.ninio.http.util.annotations.BodyParameter;
import com.davfx.ninio.http.util.annotations.DefaultValue;
import com.davfx.ninio.http.util.annotations.Directory;
import com.davfx.ninio.http.util.annotations.Header;
import com.davfx.ninio.http.util.annotations.HeaderParameter;
import com.davfx.ninio.http.util.annotations.Path;
import com.davfx.ninio.http.util.annotations.PathParameter;
import com.davfx.ninio.http.util.annotations.QueryParameter;
import com.davfx.ninio.http.util.annotations.Route;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMultimap;

public final class AnnotatedHttpService implements AutoCloseable, Closeable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AnnotatedHttpService.class);
	
	private static final class MethodParameter {
		public static enum From {
			QUERY, PATH, HEADER, BODY
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

	public final HttpService service;

	public AnnotatedHttpService(Queue queue, Address address) {
		service = new HttpService(queue, address);
	}
	
	@Override
	public void close() {
		service.close();
	}
	
	public AnnotatedHttpService register(Class<? extends HttpController> clazz) {
		LOGGER.debug("Service class: {}", clazz);
		Path controllerPath = (Path) clazz.getAnnotation(Path.class);
		HttpQueryPath pathPrefix = (controllerPath == null) ? null : HttpQueryPath.of(controllerPath.value());
		List<PathComponent> pathComponents = new LinkedList<>();
		int pathVariableIndex = 0;
		if (pathPrefix != null) {
			for (String p : pathPrefix.path) {
				pathComponents.add(PathComponent.of(p, pathVariableIndex));
				pathVariableIndex++;
			}
		}
		
		Header controllerHeader = (Header) clazz.getAnnotation(Header.class);
		final String controllerHeaderKey = (controllerHeader == null) ? null : controllerHeader.key();
		final String controllerHeaderValue = (controllerHeader == null) ? null : controllerHeader.value();

		for (final Method method : clazz.getMethods()) {
            Route route = (Route) method.getAnnotation(Route.class);
			if (route != null) {
				final HttpMethod routeMethod = route.method();
				
				HttpPath pathSuffix = route.path().isEmpty() ? null : new HttpPath(route.path());
				
				final List<PathComponent> routePathComponents = new LinkedList<>();
				routePathComponents.addAll(pathComponents);
				if (pathSuffix != null) {
					int routePathVariableIndex = pathVariableIndex;
					for (String p : pathSuffix.path.path) {
						routePathComponents.add(PathComponent.of(p, routePathVariableIndex));
						routePathVariableIndex++;
					}
				}
				
				final ImmutableMultimap<String, Optional<String>> pathParameters = (pathSuffix == null) ? null : pathSuffix.parameters;
				
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
						if (a.annotationType() == HeaderParameter.class) {
							q = new MethodParameter(((HeaderParameter) a).value(), MethodParameter.From.HEADER);
						}
						if (a.annotationType() == BodyParameter.class) {
							q = new MethodParameter(((BodyParameter) a).value(), MethodParameter.From.BODY);
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

				final Map<String, Integer> pathComponentNameToIndex = new HashMap<>();
				int pathComponentIndex = 0;
				for (PathComponent pathComponent : routePathComponents) {
					pathComponentNameToIndex.put(pathComponent.name, pathComponentIndex);
					pathComponentIndex++;
				}
				
				HttpServiceHandler handler = new HttpServiceHandler() {
					@Override
					public HttpController.Http handle(HttpRequest request, HttpPost post) throws Exception {
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
							
							MethodParameter p = parameters[i];
							String defaultValue = defaultValues[i];
							if (p == null) {
								args[i] = defaultValue;
							} else {
								String v;
								switch (p.from) {
								case QUERY: {
									Iterator<Optional<String>> it = request.path.parameters.get(p.name).iterator();
									if (!it.hasNext()) {
										v = defaultValue;
									} else {
										Optional<String> o = it.next();
										if (o.isPresent()) {
											v = o.get();
										} else {
											v = null;
										}
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
								case BODY: {
									Iterator<Optional<String>> it = post.parameters().get(p.name).iterator();
									if (!it.hasNext()) {
										v = defaultValue;
									} else {
										Optional<String> o = it.next();
										if (o.isPresent()) {
											v = o.get();
										} else {
											v = null;
										}
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
							return (HttpController.Http) method.invoke(object, args);
						} catch (Exception e) {
							throw new IOException(e);
						}
					}
				};
				
				service.register(new HttpRequestFilter() {
					@Override
					public boolean accept(HttpRequest request) {
						if (!filterHeader(request, controllerHeaderKey, controllerHeaderValue)) {
							return false;
						}
						
						if (request.method != routeMethod) {
							return false;
						}
						
						if (!filterPathParameter(request, pathParameters)) {
							return false;
						}

						return filterPath(request, routePathComponents);
					}
				}, handler);
			}
		}

        Directory directory = (Directory) clazz.getAnnotation(Directory.class);
        if (directory != null) {
			HttpServiceHandler handler = new FileHttpServiceHandler(new File(directory.root()), directory.index(), pathPrefix);
			final List<PathComponent> routePathComponents = pathComponents;

			service.register(new HttpRequestFilter() {
				@Override
				public boolean accept(HttpRequest request) {
					if (!filterHeader(request, controllerHeaderKey, controllerHeaderValue)) {
						return false;
					}
					
					if (request.method != HttpMethod.GET) {
						return false;
					}
					
					return filterPath(request, routePathComponents);
				}
			}, handler);
		}
		
		return this;
	}
	
	private static boolean filterHeader(HttpRequest request, String controllerHeaderKey, String controllerHeaderValue) {
		if ((controllerHeaderKey != null) && (controllerHeaderValue != null)) {
			boolean found = false;
			for (String v : request.headers.get(controllerHeaderKey)) {
				if (v.equals(controllerHeaderValue)) {
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}
	
	private static boolean filterPathParameter(HttpRequest request, ImmutableMultimap<String, Optional<String>> pathParameters) {
		if (pathParameters != null) {
			for (Map.Entry<String, Optional<String>> e : pathParameters.entries()) {
				String k = e.getKey();
				Optional<String> v = e.getValue();
				if (v.isPresent()) {
					String vv = v.get();
					boolean found = false;
					for (Optional<String> o : request.path.parameters.get(k)) {
						if (o.isPresent() && o.get().equals(vv)) {
							found = true;
							break;
						}
					}
					if (!found) {
						return false;
					}
				} else {
					if (request.path.parameters.get(k).isEmpty()) {
						return false;
					}
				}
			}
		}
		return true;
	}
	
	private static boolean filterPath(HttpRequest request, List<PathComponent> routePathComponents) {
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
}
