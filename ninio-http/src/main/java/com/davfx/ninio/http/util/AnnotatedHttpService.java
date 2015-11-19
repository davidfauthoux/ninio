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

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.http.HttpHeaderValue;
import com.davfx.ninio.http.HttpMethod;
import com.davfx.ninio.http.HttpPath;
import com.davfx.ninio.http.HttpQueryPath;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpRequestFilter;
import com.davfx.ninio.http.HttpStatus;
import com.davfx.ninio.http.util.HttpController.HttpWrap;
import com.davfx.ninio.http.util.annotations.BodyParameter;
import com.davfx.ninio.http.util.annotations.DefaultValue;
import com.davfx.ninio.http.util.annotations.Header;
import com.davfx.ninio.http.util.annotations.HeaderParameter;
import com.davfx.ninio.http.util.annotations.Intercept;
import com.davfx.ninio.http.util.annotations.Path;
import com.davfx.ninio.http.util.annotations.PathParameter;
import com.davfx.ninio.http.util.annotations.QueryParameter;
import com.davfx.ninio.http.util.annotations.Route;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
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

	private final HttpService service;
	private final List<HttpServiceHandler> allInterceptHandlers = new LinkedList<>();
	private final Map<Class<?>, ParameterConverter<?>> stringConverters = new HashMap<>();

	public AnnotatedHttpService(Queue queue, Address address) {
		service = new HttpService(queue, address);
		
		stringConverters.put(Boolean.class, new ParameterConverter<Boolean>() {
			@Override
			public Boolean of(String s) throws Exception {
				return Boolean.parseBoolean(s);
			}
		});
		stringConverters.put(Byte.class, new ParameterConverter<Byte>() {
			@Override
			public Byte of(String s) throws Exception {
				return Byte.parseByte(s);
			}
		});
		stringConverters.put(Short.class, new ParameterConverter<Short>() {
			@Override
			public Short of(String s) throws Exception {
				return Short.parseShort(s);
			}
		});
		stringConverters.put(Integer.class, new ParameterConverter<Integer>() {
			@Override
			public Integer of(String s) throws Exception {
				return Integer.parseInt(s);
			}
		});
		stringConverters.put(Long.class, new ParameterConverter<Long>() {
			@Override
			public Long of(String s) throws Exception {
				return Long.parseLong(s);
			}
		});
		stringConverters.put(Character.class, new ParameterConverter<Character>() {
			@Override
			public Character of(String s) throws Exception {
				return s.charAt(0);
			}
		});
		stringConverters.put(Float.class, new ParameterConverter<Float>() {
			@Override
			public Float of(String s) throws Exception {
				return Float.parseFloat(s);
			}
		});
		stringConverters.put(Double.class, new ParameterConverter<Double>() {
			@Override
			public Double of(String s) throws Exception {
				return Double.parseDouble(s);
			}
		});
	}
	
	@Override
	public void close() {
		service.close();
	}
	
	public <T> AnnotatedHttpService parameters(Class<T> clazz, ParameterConverter<T> converter) {
		stringConverters.put(clazz, converter);
		return this;
	}
	
	public <T> AnnotatedHttpService parameters(final Class<T> clazz) {
		final Method m;
		try {
			m = clazz.getDeclaredMethod("of", String.class);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
		try {
			m.setAccessible(true);
		} catch (Throwable e) {
		}
		stringConverters.put(clazz, new ParameterConverter<T>() {
			@Override
			public T of(String s) throws Exception {
				Object r = m.invoke(null, s);
				@SuppressWarnings("unchecked")
				T t = (T) r;
				return t;
			}
		});
		return this;
	}
	
	private ParameterConverter<?> getConverter(Class<?> clazz) {
		if (clazz == boolean.class) {
			clazz = Boolean.class;
		}
		if (clazz == byte.class) {
			clazz = Byte.class;
		}
		if (clazz == short.class) {
			clazz = Short.class;
		}
		if (clazz == int.class) {
			clazz = Integer.class;
		}
		if (clazz == long.class) {
			clazz = Long.class;
		}
		if (clazz == char.class) {
			clazz = Character.class;
		}
		if (clazz == float.class) {
			clazz = Float.class;
		}
		if (clazz == double.class) {
			clazz = Double.class;
		}
		return stringConverters.get(clazz);
	}
	
	private static <T> T newInstance(Class<? extends T> clazz) {
		try {
			return clazz.newInstance();
		} catch (Exception e) {
			LOGGER.warn("Could not create object from pre class {}", clazz, e);
			throw new RuntimeException(e);
		}
	}
	
	private List<HttpServiceHandler> createInterceptHandlers(final HttpController object) {
		List<HttpServiceHandler> handlers = new LinkedList<>();

		for (final Method method : object.getClass().getMethods()) {

            Route route = (Route) method.getAnnotation(Route.class);
			if (route != null) {
				final HttpMethod routeMethod = route.method();
				
				Annotation[][] parameterAnnotations = method.getParameterAnnotations();
				Class<?>[] parameterTypes = method.getParameterTypes();
				final MethodParameter[] parameters = new MethodParameter[parameterAnnotations.length];
				final String[] defaultValues = new String[parameterAnnotations.length];
				final ParameterConverter<?>[] methodStringConverters = new ParameterConverter<?>[parameterAnnotations.length];
				int requestParameterIndexToSet = -1;
				int postParameterIndexToSet = -1;
				for (int i = 0; i < parameterAnnotations.length; i++) {
					methodStringConverters[i] = getConverter(parameterTypes[i]);

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

				HttpServiceHandler preHandler = new HttpServiceHandler() {
					@Override
					public HttpController.Http handle(HttpRequest request, final HttpPost sourcePost) throws Exception {
						if (request.method != routeMethod) {
							return null;
						}
						
						HttpPost post = new HttpPost() {
							@Override
							public InputStream stream() {
								// Forbidden
								return null;
							}
							@Override
							public ImmutableMultimap<String, Optional<String>> parameters() {
								return sourcePost.parameters();
							}
							@Override
							public String toString() {
								return sourcePost.toString();
							}
						};

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
							String v;
							if (p == null) {
								v = null;
							} else {
								switch (p.from) {
								case QUERY: {
									Iterator<Optional<String>> it = request.path.parameters.get(p.name).iterator();
									if (!it.hasNext()) {
										v = null;
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
								case HEADER: {
									Iterator<HttpHeaderValue> it = request.headers.get(p.name).iterator();
									if (!it.hasNext()) {
										v = null;
									} else {
										v = it.next().asString();
									}
									break;
								}
								case BODY: {
									Iterator<Optional<String>> it = post.parameters().get(p.name).iterator();
									if (!it.hasNext()) {
										v = null;
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
									v = null;
									break;
								}
							}
							
							String defaultValue = defaultValues[i];
							if (v == null) {
								v = defaultValue;
							}
							
							Object o;
							if (methodStringConverters[i] != null) {
								o = methodStringConverters[i].of(v);
							} else {
								o = v;
							}
							
							args[i] = o;
						}

						try {
							return (HttpController.Http) method.invoke(object, args);
						} catch (Exception e) {
							throw new IOException(e);
						}
					}
				};
				
				handlers.add(preHandler);
			}
		}
		
		return handlers;
	}

	public AnnotatedHttpService intercept(Class<? extends HttpController> clazz) {
		return intercept(newInstance(clazz));
	}
	
	public AnnotatedHttpService intercept(HttpController controller) {
		allInterceptHandlers.addAll(createInterceptHandlers(controller));
		return this;
	}
	
	public AnnotatedHttpService register(Class<? extends HttpController> clazz) {
		Path controllerPath = (Path) clazz.getAnnotation(Path.class);
		HttpQueryPath pathPrefix = (controllerPath == null) ? null : HttpQueryPath.of(controllerPath.value());
		return register(pathPrefix, newInstance(clazz));
	}
	
	public AnnotatedHttpService register(HttpQueryPath pathPrefix, final HttpController controller) {
		final List<PathComponent> pathComponents = new LinkedList<>();
		int pathVariableIndex = 0;
		if (pathPrefix != null) {
			for (String p : pathPrefix.path) {
				pathComponents.add(PathComponent.of(p, pathVariableIndex));
				pathVariableIndex++;
			}
		}
		
		Header controllerHeader = (Header) controller.getClass().getAnnotation(Header.class);
		final String controllerHeaderKey = (controllerHeader == null) ? null : controllerHeader.key();
		final String controllerHeaderValue = (controllerHeader == null) ? null : controllerHeader.value();
		
		for (final Method method : controller.getClass().getMethods()) {
			final List<HttpServiceHandler> interceptHandlers = new LinkedList<>();
			interceptHandlers.addAll(allInterceptHandlers);
			
            Intercept intercept = (Intercept) method.getAnnotation(Intercept.class);
			if (intercept != null) {
				interceptHandlers.addAll(createInterceptHandlers(newInstance(intercept.value())));
			}
			
            Route route = (Route) method.getAnnotation(Route.class);
			if (route != null) {
				final HttpMethod routeMethod = route.method();
				
				HttpPath pathSuffix = route.path().isEmpty() ? null : HttpPath.of(route.path());
				
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
				
				Annotation[][] parameterAnnotations = method.getParameterAnnotations();
				Class<?>[] parameterTypes = method.getParameterTypes();
				final MethodParameter[] parameters = new MethodParameter[parameterAnnotations.length];
				final String[] defaultValues = new String[parameterAnnotations.length];
				final ParameterConverter<?>[] methodStringConverters = new ParameterConverter<?>[parameterAnnotations.length];
				int requestParameterIndexToSet = -1;
				int postParameterIndexToSet = -1;
				for (int i = 0; i < parameterAnnotations.length; i++) {
					methodStringConverters[i] = getConverter(parameterTypes[i]);

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
					public HttpController.Http handle(HttpRequest sourceRequest, HttpPost post) throws Exception {
						ImmutableList.Builder<String> pathWithoutBeginning = ImmutableList.builder();
						int pathIndex = 0;
						for (String s : sourceRequest.path.path.path) {
							if (pathIndex > pathComponents.size()) {
								pathWithoutBeginning.add(s);
							}
							pathIndex++;
						}
						HttpRequest request = new HttpRequest(sourceRequest.address, sourceRequest.secure, sourceRequest.method, new HttpPath(new HttpQueryPath(pathWithoutBeginning.build()), sourceRequest.path.parameters, sourceRequest.path.hash), sourceRequest.headers);
						
						List<HttpWrap> wraps = new LinkedList<>();
						for (HttpServiceHandler interceptHandler : interceptHandlers) {
							HttpController.Http interceptHttp = interceptHandler.handle(request, post);
							if (interceptHttp != null) {
								if (interceptHttp.status != HttpStatus.OK) {
									return interceptHttp;
								}
								if (interceptHttp.wrap != null) {
									wraps.add(interceptHttp.wrap);
								}
							}
						}
						
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
							String v;
							if (p == null) {
								v = null;
							} else {
								switch (p.from) {
								case QUERY: {
									Iterator<Optional<String>> it = request.path.parameters.get(p.name).iterator();
									if (!it.hasNext()) {
										v = null;
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
										v = null;
									} else {
										v = sourceRequest.path.path.path.get(index);
									}
									break;
								}
								case HEADER: {
									Iterator<HttpHeaderValue> it = request.headers.get(p.name).iterator();
									if (!it.hasNext()) {
										v = null;
									} else {
										v = it.next().asString();
									}
									break;
								}
								case BODY: {
									Iterator<Optional<String>> it = post.parameters().get(p.name).iterator();
									if (!it.hasNext()) {
										v = null;
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
									v = null;
									break;
								}
							}

							String defaultValue = defaultValues[i];
							if (v == null) {
								v = defaultValue;
							}
							
							Object o;
							if (methodStringConverters[i] != null) {
								o = methodStringConverters[i].of(v);
							} else {
								o = v;
							}
							
							args[i] = o;
						}

						HttpController.Http http;
						try {
							http = (HttpController.Http) method.invoke(controller, args);
						} catch (Exception e) {
							throw new IOException(e);
						}
						
						if (http.wrap != null) {
							wraps.add(http.wrap);
						}

						for (HttpWrap w : wraps) {
							w.handle(http);
						}
						
						return http;
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
		/*%%
        Directory directory = (Directory) controller.getClass().getAnnotation(Directory.class);
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
		*/
		
		return this;
	}
	
	private static boolean filterHeader(HttpRequest request, String controllerHeaderKey, String controllerHeaderValue) {
		if ((controllerHeaderKey != null) && (controllerHeaderValue != null)) {
			boolean found = false;
			for (HttpHeaderValue v : request.headers.get(controllerHeaderKey)) {
				if (v.contains(controllerHeaderValue)) {
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
