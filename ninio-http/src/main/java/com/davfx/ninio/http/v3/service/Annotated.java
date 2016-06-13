package com.davfx.ninio.http.v3.service;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.http.v3.HttpMethod;
import com.davfx.ninio.http.v3.HttpRequest;
import com.davfx.ninio.http.v3.HttpStatus;
import com.davfx.ninio.http.v3.service.HttpController.HttpWrap;
import com.davfx.ninio.http.v3.service.annotations.BodyParameter;
import com.davfx.ninio.http.v3.service.annotations.DefaultValue;
import com.davfx.ninio.http.v3.service.annotations.Header;
import com.davfx.ninio.http.v3.service.annotations.HeaderParameter;
import com.davfx.ninio.http.v3.service.annotations.Intercept;
import com.davfx.ninio.http.v3.service.annotations.Path;
import com.davfx.ninio.http.v3.service.annotations.PathParameter;
import com.davfx.ninio.http.v3.service.annotations.QueryParameter;
import com.davfx.ninio.http.v3.service.annotations.Route;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.reflect.TypeToken;

public final class Annotated {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Annotated.class);
	
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

	private static final class ConverterElement<T> {
		public final TypeToken<T> type;
		public final ParameterConverter<T> converter;
		public ConverterElement(Class<T> type, ParameterConverter<T> converter) {
			this(TypeToken.of(type), converter);
		}
		public ConverterElement(TypeToken<T> type, ParameterConverter<T> converter) {
			this.type = type;
			this.converter = converter;
		}
	}
	
	private final HttpService.Builder builder;
	
	private final List<HttpServiceHandler> allInterceptHandlers = new LinkedList<>();
	private final List<ConverterElement<?>> stringConverters = new LinkedList<>();

	public Annotated(HttpService.Builder builder) {
		this.builder = builder;
		
		stringConverters.add(new ConverterElement<Boolean>(Boolean.class, new ParameterConverter<Boolean>() {
			@Override
			public Boolean of(String s) throws Exception {
				return Boolean.parseBoolean(s);
			}
		}));
		stringConverters.add(new ConverterElement<Byte>(Byte.class, new ParameterConverter<Byte>() {
			@Override
			public Byte of(String s) throws Exception {
				return Byte.parseByte(s);
			}
		}));
		stringConverters.add(new ConverterElement<Short>(Short.class, new ParameterConverter<Short>() {
			@Override
			public Short of(String s) throws Exception {
				return Short.parseShort(s);
			}
		}));
		stringConverters.add(new ConverterElement<Integer>(Integer.class, new ParameterConverter<Integer>() {
			@Override
			public Integer of(String s) throws Exception {
				return Integer.parseInt(s);
			}
		}));
		stringConverters.add(new ConverterElement<Long>(Long.class, new ParameterConverter<Long>() {
			@Override
			public Long of(String s) throws Exception {
				return Long.parseLong(s);
			}
		}));
		stringConverters.add(new ConverterElement<Character>(Character.class, new ParameterConverter<Character>() {
			@Override
			public Character of(String s) throws Exception {
				return s.charAt(0);
			}
		}));
		stringConverters.add(new ConverterElement<Float>(Float.class, new ParameterConverter<Float>() {
			@Override
			public Float of(String s) throws Exception {
				return Float.parseFloat(s);
			}
		}));
		stringConverters.add(new ConverterElement<Double>(Double.class, new ParameterConverter<Double>() {
			@Override
			public Double of(String s) throws Exception {
				return Double.parseDouble(s);
			}
		}));
		stringConverters.add(new ConverterElement<String>(String.class, new ParameterConverter<String>() {
			@Override
			public String of(String s) throws Exception {
				return s;
			}
		}));
	}
	
	public <T> Annotated parameters(TypeToken<T> clazz, ParameterConverter<T> converter) {
		stringConverters.add(new ConverterElement<>(clazz, converter));
		return this;
	}
	
	public <T> Annotated parameters(Class<T> clazz, ParameterConverter<T> converter) {
		stringConverters.add(new ConverterElement<>(clazz, converter));
		return this;
	}
	
	public <T> Annotated parameters(final Class<T> clazz) {
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
		stringConverters.add(new ConverterElement<>(clazz, new ParameterConverter<T>() {
			@Override
			public T of(String s) throws Exception {
				Object r = m.invoke(null, s);
				@SuppressWarnings("unchecked")
				T t = (T) r;
				return t;
			}
		}));
		return this;
	}
	
	private ParameterConverter<?> getConverter(Type clazz) {
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
		TypeToken<?> t = TypeToken.of(clazz);
		for (ConverterElement<?> c : stringConverters) {
			LOGGER.trace("Comparing {} == {}", t, c.type);
			if (c.type.equals(t)) {
				return c.converter;
			}
			if (c.type.isAssignableFrom(t)) {
				return c.converter;
			}
		}
		throw new IllegalArgumentException("No converter found for: " + clazz);
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
				Type[] parameterTypes = method.getGenericParameterTypes();
				final MethodParameter[] parameters = new MethodParameter[parameterAnnotations.length];
				final String[] defaultValues = new String[parameterAnnotations.length];
				final ParameterConverter<?>[] methodStringConverters = new ParameterConverter<?>[parameterAnnotations.length];
				int requestParameterIndexToSet = -1;
				int postParameterIndexToSet = -1;
				for (int i = 0; i < parameterAnnotations.length; i++) {
					if (parameterTypes[i] == HttpServiceRequest.class) {
						methodStringConverters[i] = null;
						requestParameterIndexToSet = i;
						parameters[i] = null;
						continue;
					}

					if (parameterTypes[i] == HttpPost.class) {
						methodStringConverters[i] = null;
						postParameterIndexToSet = i;
						parameters[i] = null;
						continue;
					}

					methodStringConverters[i] = getConverter(parameterTypes[i]);

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
					public HttpController.Http handle(HttpServiceRequest request, final HttpPost sourcePost) throws Exception {
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
									Iterator<Optional<String>> it = request.parameters.get(p.name).iterator();
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
									Iterator<String> it = request.headers.get(p.name).iterator();
									if (!it.hasNext()) {
										v = null;
									} else {
										v = it.next();
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

	public Annotated intercept(Class<? extends HttpController> clazz) {
		return intercept(newInstance(clazz));
	}
	
	public Annotated intercept(HttpController controller) {
		allInterceptHandlers.addAll(createInterceptHandlers(controller));
		return this;
	}
	
	public Annotated register(Class<? extends HttpController> clazz) {
		return register(newInstance(clazz));
	}
	
	public Annotated register(final HttpController controller) {
		Header controllerHeader = (Header) controller.getClass().getAnnotation(Header.class);
		final String controllerHeaderKey = (controllerHeader == null) ? null : controllerHeader.key();
		final Pattern controllerHeaderPattern = (controllerHeader == null) ? null : Pattern.compile(controllerHeader.pattern());
		
		for (final Method method : controller.getClass().getMethods()) {
			final List<HttpServiceHandler> interceptHandlers = new LinkedList<>();
			interceptHandlers.addAll(allInterceptHandlers);
			
            Intercept intercept = (Intercept) method.getAnnotation(Intercept.class);
			if (intercept != null) {
				interceptHandlers.addAll(createInterceptHandlers(newInstance(intercept.value())));
			}
			
            Route route = (Route) method.getAnnotation(Route.class);
			if (route != null) {
				Path controllerPath = (Path) controller.getClass().getAnnotation(Path.class);

				final HttpMethod routeMethod = route.method();
				
				String controllerPathSuffix = (controllerPath == null) ? null : controllerPath.value();
				String pathSuffix = route.path().isEmpty() ? null : route.path();
				
				final List<PathComponent> routePathComponents = new LinkedList<>();
				int routePathVariableIndex = 0;
				if (controllerPathSuffix != null) {
					for (String p : HttpRequest.path(controllerPathSuffix)) {
						routePathComponents.add(PathComponent.of(p, routePathVariableIndex));
						routePathVariableIndex++;
					}
				}
				if (pathSuffix != null) {
					for (String p : HttpRequest.path(pathSuffix)) {
						routePathComponents.add(PathComponent.of(p, routePathVariableIndex));
						routePathVariableIndex++;
					}
				}

				ImmutableMultimap.Builder<String, Optional<String>> pathParametersBuilder = ImmutableMultimap.builder();
				if (controllerPathSuffix != null) {
					pathParametersBuilder.putAll(HttpRequest.parameters(controllerPathSuffix));
				}
				if (pathSuffix != null) {
					pathParametersBuilder.putAll(HttpRequest.parameters(pathSuffix));
				}
				final ImmutableMultimap<String, Optional<String>> pathParameters = pathParametersBuilder.build();
				
				Annotation[][] parameterAnnotations = method.getParameterAnnotations();
				Type[] parameterTypes = method.getGenericParameterTypes();
				final MethodParameter[] parameters = new MethodParameter[parameterAnnotations.length];
				final String[] defaultValues = new String[parameterAnnotations.length];
				final ParameterConverter<?>[] methodStringConverters = new ParameterConverter<?>[parameterAnnotations.length];
				int requestParameterIndexToSet = -1;
				int postParameterIndexToSet = -1;
				for (int i = 0; i < parameterAnnotations.length; i++) {
					if (parameterTypes[i] == HttpServiceRequest.class) {
						methodStringConverters[i] = null;
						requestParameterIndexToSet = i;
						parameters[i] = null;
						continue;
					}
					
					if (parameterTypes[i] == HttpPost.class) {
						methodStringConverters[i] = null;
						postParameterIndexToSet = i;
						parameters[i] = null;
						continue;
					}

					methodStringConverters[i] = getConverter(parameterTypes[i]);

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
				
				builder.register(new HttpServiceHandler() {
					@Override
					public HttpController.Http handle(HttpServiceRequest request, HttpPost post) throws Exception {
						if (!filterHeader(request.headers, controllerHeaderKey, controllerHeaderPattern)) {
							return null;
						}
						
						if (request.method != routeMethod) {
							return null;
						}
						
						if (!filterPathParameter(request.parameters, pathParameters)) {
							return null;
						}

						if (!filterPath(request.path, routePathComponents)) {
							return null;
						}
						
						//

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
									Iterator<Optional<String>> it = request.parameters.get(p.name).iterator();
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
										v = request.path.get(index);
									}
									break;
								}
								case HEADER: {
									Iterator<String> it = request.headers.get(p.name).iterator();
									if (!it.hasNext()) {
										v = null;
									} else {
										v = it.next();
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
				});
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
	
	private static boolean filterHeader(ImmutableMultimap<String, String> requestHeaders, String controllerHeaderKey, Pattern controllerHeaderPattern) {
		if ((controllerHeaderKey != null) && (controllerHeaderPattern != null)) {
			boolean found = false;
			for (String v : requestHeaders.get(controllerHeaderKey)) {
				if (controllerHeaderPattern.matcher(v).matches()) {
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
	
	private static boolean filterPathParameter(ImmutableMultimap<String, Optional<String>> requestParameters, ImmutableMultimap<String, Optional<String>> pathParameters) {
		for (Map.Entry<String, Optional<String>> e : pathParameters.entries()) {
			String k = e.getKey();
			Optional<String> v = e.getValue();
			if (v.isPresent()) {
				String vv = v.get();
				boolean found = false;
				for (Optional<String> o : requestParameters.get(k)) {
					if (o.isPresent() && o.get().equals(vv)) {
						found = true;
						break;
					}
				}
				if (!found) {
					return false;
				}
			} else {
				if (requestParameters.get(k).isEmpty()) {
					return false;
				}
			}
		}
		return true;
	}
	
	private static boolean filterPath(ImmutableList<String> path, List<PathComponent> routePathComponents) {
		Iterator<String> i = path.iterator();
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
