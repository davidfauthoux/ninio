package com.davfx.ninio.http.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.http.HttpQueryPath;
import com.davfx.ninio.http.HttpRequest;
import com.google.common.reflect.ClassPath;

public final class AnnotatedHttpService {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AnnotatedHttpService.class);
	
	private static final class MethodParameter {
		public static enum From {
			QUERY, PATH
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
	
	private AnnotatedHttpService() {
	}
	
	public static HttpService service(ClassLoader... classLoaders) {
		HttpService service = new HttpService();
		
		for (ClassLoader classLoader : classLoaders) {
			ClassPath classPath;
			try {
				classPath = ClassPath.from(classLoader);
			} catch (IOException ioe) {
				LOGGER.warn("Could not get class path", ioe);
				continue;
			}
			
			for (ClassPath.ClassInfo classInfo : classPath.getAllClasses()) {
				Class<?> clazz;
				try {
					clazz = classInfo.load();
				} catch (LinkageError e) {
					continue;
				}
				HttpController controller = (HttpController) clazz.getAnnotation(HttpController.class);
				if (controller != null) {
					LOGGER.debug("Service class: {}", clazz);
					HttpQueryPath pathPrefix = controller.value().isEmpty() ? null : HttpQueryPath.of(controller.value());
					List<PathComponent> pathComponents = new LinkedList<>();
					int pathVariableIndex = 0;
					if (pathPrefix != null) {
						for (String p : pathPrefix.path) {
							pathComponents.add(PathComponent.of(p, pathVariableIndex));
							pathVariableIndex++;
						}
					}
					for (final Method method : clazz.getMethods()) {
		                HttpRoute route = (HttpRoute) method.getAnnotation(HttpRoute.class);
						if (route != null) {
							HttpQueryPath pathSuffix = route.value().isEmpty() ? null : HttpQueryPath.of(route.value());
							
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
							int requestParameterIndexToSet = -1;
							int postParameterIndexToSet = -1;
							int resultParameterIndexToSet = -1;
							for (int i = 0; i < parameterAnnotations.length; i++) {
								if (parameterTypes[i] == HttpRequest.class) {
									requestParameterIndexToSet = i;
									parameters[i] = null;
									continue;
								}
								
								if (parameterTypes[i] == InputStream.class) {
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
								for (Annotation a : an) {
									if (a.annotationType() == HttpQueryParameter.class) {
										p = new MethodParameter(((HttpQueryParameter) a).value(), MethodParameter.From.QUERY);
										break;
									}
									if (a.annotationType() == HttpPathParameter.class) {
										p = new MethodParameter(((HttpPathParameter) a).value(), MethodParameter.From.PATH);
										break;
									}
								}
								parameters[i] = p;
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
								public void handle(HttpRequest request, InputStream post, HttpServiceResult result) throws IOException {
									
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
										
										String v;
										MethodParameter p = parameters[i];
										switch (p.from) {
										case QUERY: {
											Iterator<String> it = request.path.parameters.get(p.name).iterator();
											if (!it.hasNext()) {
												v = null;
											} else {
												v = it.next();
											}
											break;
										}
										case PATH: {
											Integer index = pathComponentNameToIndex.get(p.name);
											if (index == null) {
												v = null;
											} else {
												v = request.path.path.path.get(index);
											}
											break;
										}
										default:
											v = null;
											break;
										}
										args[i] = v;
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
				}
			}
		}
		
		return service;
	}
}
