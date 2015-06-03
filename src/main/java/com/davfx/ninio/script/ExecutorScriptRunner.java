package com.davfx.ninio.script;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.ClassThreadFactory;
import com.davfx.ninio.common.Failable;
import com.davfx.ninio.util.CheckAllocationObject;
import com.davfx.util.ConfigUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

public final class ExecutorScriptRunner extends CheckAllocationObject implements ScriptRunner, AutoCloseable {
	/*%%%
	public static void main(String[] args) throws Exception {
		ScriptRunner r = new ExecutorScriptRunner();
		r.register("echo", new AsyncScriptFunction() {
			@Override
			public void call(JsonElement request, Callback callback) {
				JsonObject o = new JsonObject();
				o.add("request", request);
				o.add("response", new JsonPrimitive("This is a response"));
				callback.handle(o);
			}
		});
		r.register("eeecho3", new SyncScriptFunction() {
			@Override
			public JsonElement call(JsonElement request) {
				JsonObject o = new JsonObject();
				o.add("request", request);
				o.add("response", new JsonPrimitive("This is a response 3333"));
				return o;
			}
		});
		r.prepare("var aaa = 'aaaaaa';echo('a', function(r) { println('ECHO ' + JSON.stringify(r)); });", null, null);
		r.prepare("println('ECHO3 ' + JSON.stringify(eeecho3('a')));", null, null);

		ScriptRunner.Engine e = r.engine();
		e.register("echo2", new AsyncScriptFunction() {
			@Override
			public void call(JsonElement request, Callback callback) {
				JsonObject o = new JsonObject();
				o.add("request", request);
				o.add("response", new JsonPrimitive("This is a response 2"));
				callback.handle(o);
			}
		});
		e.register("echo3", new SyncScriptFunction() {
			@Override
			public JsonElement call(JsonElement request) {
				JsonObject o = new JsonObject();
				o.add("request", request);
				o.add("response", new JsonPrimitive("This is a response 3"));
				return o;
			}
		});
		e.eval("var aaa = 'aaa'; echo2('a', function(r) { println(aaa + ' ECHO2 ' + JSON.stringify(r)); });", null, null);
		e.eval("echo2('a', function(r) { println(aaa + ' ECHO2 ' + JSON.stringify(r)); });", null, null);
		e.eval("println('ECHO3 ' + JSON.stringify(echo3('a')));", null, null);
		
		Thread.sleep(1000);
		System.exit(0);
	}*/
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorScriptRunner.class);
	
	private static final Config CONFIG = ConfigUtils.load(ExecutorScriptRunner.class);
	
	private static final boolean USE_TO_STRING;
	static {
		String mode = CONFIG.getString("script.mode");
		if (mode.equals("string")) {
			USE_TO_STRING = true;
			LOGGER.debug("Mode: string");
		} else if (mode.equals("json")) {
			USE_TO_STRING = false;
			LOGGER.debug("Mode: json");
		} else {
			throw new ConfigException.BadValue("script.mode", "Invalid mode, only allowed: json|string");
		}
	}
	
	private static final String ENGINE_NAME = CONFIG.getString("script.engine");
	static {
		LOGGER.debug("Engine: {}", ENGINE_NAME);
	}
	public static final String CALL_FUNCTION_NAME = CONFIG.getString("script.functions.call");
	//TODO public static final String UNICITY_PREFIX = CONFIG.getString("script.functions.unicity.prefix");
	
	private final ScriptEngine scriptEngine;
	private final ExecutorService executorService = Executors.newSingleThreadExecutor(new ClassThreadFactory(ExecutorScriptRunner.class));
	private int nextUnicityId = 0;
	private final Map<String, EndManager> endManagers = new HashMap<>();

	public ExecutorScriptRunner() {
		super(ExecutorScriptRunner.class);
		
		ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
		
		scriptEngine = scriptEngineManager.getEngineByName(ENGINE_NAME);
		if (scriptEngine == null) {
			throw new IllegalArgumentException("Bad engine: " + ENGINE_NAME);
		}
		LOGGER.debug("Script engine {}/{}", scriptEngine.getFactory().getEngineName(), scriptEngine.getFactory().getEngineVersion());

		try {
			scriptEngine.eval(ScriptUtils.functions());
			scriptEngine.eval(""
					+ "var __$nextUnicityId = 0;"
					+ "var __$callbacks = {};"
					+ "var __$instanceId = null;"
				);
			
			if (!USE_TO_STRING) {
				scriptEngine.eval(""
						+ "var __$convertFrom = function(o) {"
							+ "if (o == null) {"
								+ "return null;"
							+ "}"
							+ "if (o instanceof Array) {"
								+ "var p = new com.google.gson.JsonArray();"
								+ "for (k in o) {"
									+ "p.add(__$convertFrom(o[k]));"
								+ "}"
								+ "return p;"
							+ "}"
							+ "if (o instanceof Object) {"
								+ "var p = new com.google.gson.JsonObject();"
								+ "for (k in o) {"
									+ "p.add(k, __$convertFrom(o[k]));"
								+ "}"
								+ "return p;"
							+ "}"
							+ "if (typeof o == \"string\") {"
								+ "return " + ExecutorScriptRunner.class.getName() + ".jsonString(o);"
							+ "}"
							+ "if (typeof o == \"number\") {"
								+ "return " + ExecutorScriptRunner.class.getName() + ".jsonNumber(o);"
							+ "}"
							+ "if (typeof o == \"boolean\") {"
								+ "return " + ExecutorScriptRunner.class.getName() + ".jsonBoolean(o);"
							+ "}"
						+ "};"
					+ "var __$convertTo = function(o) {"
						+ "if (o == null) {"
							+ "return null;"
						+ "}"
						+ "if (o.isJsonObject()) {"
							+ "var i = o.entrySet().iterator();"
							+ "var p = {};"
							+ "while (i.hasNext()) {"
								+ "var e = i.next();"
								+ "p[e.getKey()] = __$convertTo(e.getValue());"
							+ "}"
							+ "return p;"
						+ "}"
						+ "if (o.isJsonPrimitive()) {"
							+ "var oo = o.getAsJsonPrimitive();"
							+ "if (oo.isString()) {"
								+ "return '' + oo.getAsString();" // ['' +] looks to be necessary
							+ "}"
							+ "if (oo.isNumber()) {"
								+ "return oo.getAsDouble();" //TODO Check long precision??? 
							+ "}"
							+ "if (oo.isBoolean()) {"
								+ "return oo.getAsBoolean();"
							+ "}"
							+ "return null;"
						+ "}"
						+ "return null;"
					+ "};");
			}
		} catch (ScriptException se) {
			LOGGER.error("Could not initialize script engine", se);
		}
	}
	
	@Override
	public void close() {
		LOGGER.debug("Script engine closed");
		executorService.shutdown();
	}
	
	private static final class EndManager extends CheckAllocationObject {
		public final String instanceId;
		private int count = 0;
		private Runnable end;
		private Failable fail;
		public EndManager(String instanceId, Failable fail, Runnable end) {
			super(EndManager.class);
			this.instanceId = instanceId;
			this.end = end;
			this.fail = fail;
		}
		public void fail(IOException e) {
			LOGGER.error("Failed", e);
			end = null;
			Failable f = fail;
			fail = null;
			if (f != null) {
				f.failed(e);
			}
		}
		public void inc() {
			count++;
		}
		public void dec() {
			count--;
			if (count == 0) {
				fail = null;
				Runnable e = end;
				end = null;
				if (e != null) {
					e.run();
				}
			}
		}
	}
	
	// Must be be public to be called from javascript
	public final class SyncInternal extends CheckAllocationObject {
		private final SyncScriptFunction syncFunction;
		private SyncInternal(SyncScriptFunction syncFunction) {
			super(SyncInternal.class);
			this.syncFunction = syncFunction;
		}
		public String call(String requestAsString) {
			JsonElement request = new JsonParser().parse(requestAsString);
			JsonElement response = syncFunction.call(request);
			if (response == null) {
				return null;
			}
			return response.toString();
		}
	}
	
	// Must be be public to be called from javascript
	public final class AsyncInternal extends CheckAllocationObject {
		private final AsyncScriptFunction asyncFunction;
		private AsyncInternal(AsyncScriptFunction asyncFunction) {
			super(AsyncInternal.class);
			this.asyncFunction = asyncFunction;
		}
		public void call(final String instanceId, Object requestAsObject, final String callbackId) {
			JsonElement request;
			if (USE_TO_STRING) {
				request = new JsonParser().parse((String) requestAsObject);
			} else {
				request = (JsonElement) requestAsObject;
			}
			
			final EndManager endManager = endManagers.get(instanceId);
			if (endManager != null) {
				endManager.inc();
			}
			asyncFunction.call(request, new AsyncScriptFunction.Callback() {
				@Override
				public void handle(final JsonElement response) {
					executorService.execute(new Runnable() {
						@Override
						public void run() {
							try {
								try {
									if (USE_TO_STRING) {
										scriptEngine.eval(""
												+ "var __$f = __$callbacks['" + callbackId + "'];"
												+ "delete __$callbacks['" + callbackId + "'];"
												+ "if (__$f) __$f(" + response.toString() + ");"
											);
									} else {
										scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).put("__$response", response);
										try {
											scriptEngine.eval("var __$r = __$convertTo(__$response);");
										} finally {
											scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).remove("__$response");
										}
										
										scriptEngine.eval(""
												+ "var __$f = __$callbacks['" + callbackId + "'];"
												+ "delete __$callbacks['" + callbackId + "'];"
												+ "if (__$f) __$f(__$r);"
												+ "__$r = null;" //TODO check if required in Java7
												+ "__$r = undefined;" //TODO check if release from mem
											);
									}
								} catch (ScriptException se) {
									if (endManager != null) {
										endManager.fail(new IOException(se));
									}
								}
							} finally {
								if (endManager != null) {
									endManager.dec();
								}
							}
						}
					});
				}
			});
		}
	}
	
	@Override
	public void register(final String function, final SyncScriptFunction syncFunction) {
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				String id = String.valueOf(nextUnicityId);
				nextUnicityId++;
				
				String functionObjectVar = "__$function" + id;
				scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).put(functionObjectVar, new SyncInternal(syncFunction));
				
				try {
					if (USE_TO_STRING) {
						scriptEngine.eval(""
								+ "var " + function + " = function(p) {"
									+ "var r = " + functionObjectVar + ".call(JSON.stringify(p));"
									+ "if (r == null) {"
										+ "return r;"
									+ "}"
									+ "return JSON.parse(r);"
								+ "};"
							);
					} else {
						scriptEngine.eval(""
								+ "var " + function + " = function(p) {"
									+ "var r = " + functionObjectVar + ".call(__$convertFrom(p));"
									+ "if (r == null) {"
										+ "return r;"
									+ "}"
									+ "return __$convertTo(r);"
								+ "};"
							);
					}
				} catch (ScriptException se) {
					LOGGER.error("Could not register {}", function, se);
				}
			}
		});
	}
	
	@Override
	public void register(final String function, final AsyncScriptFunction asyncFunction) {
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				String id = String.valueOf(nextUnicityId);
				nextUnicityId++;
				
				String functionObjectVar = "__$function" + id;
				scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).put(functionObjectVar, new AsyncInternal(asyncFunction));
				
				try {
					if (USE_TO_STRING) {
						scriptEngine.eval(""
								+ "var " + function + " = function(p, callback) {"
									+ "var callbackId = '" + function + "' + __$nextUnicityId;"
									+ "__$nextUnicityId++;"
									+ "__$callbacks[callbackId] = callback;"
									+ functionObjectVar + ".call(__$instanceId, JSON.stringify(p), callbackId);"
								+ "};"
							);
					} else {
						scriptEngine.eval(""
								+ "var " + function + " = function(p, callback) {"
									+ "var callbackId = '" + function + "' + __$nextUnicityId;"
									+ "__$nextUnicityId++;"
									+ "__$callbacks[callbackId] = callback;"
									+ functionObjectVar + ".call(__$instanceId, __$convertFrom(p), callbackId);"
								+ "};"
							);
					}
				} catch (ScriptException se) {
					LOGGER.error("Could not register {}", function, se);
				}
			}
		});
	}

	private EndManager endManager(final List<String> bindingsToRemove, final Failable fail, final Runnable end) {
		final String instanceId = String.valueOf(nextUnicityId);
		nextUnicityId++;
		
		final Runnable clean = new Runnable() {
			@Override
			public void run() {
				endManagers.remove(instanceId);
				if (bindingsToRemove != null) {
					for (String functionObjectVar : bindingsToRemove) {
						scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).put(functionObjectVar, null);
						//TODO check if ok in Java8 // scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).remove(functionObjectVar);
					}
				}
			}
		};
		
		EndManager endManager = new EndManager(instanceId, new Failable() {
			@Override
			public void failed(IOException e) {
				clean.run();
				if (fail != null) {
					fail.failed(e);
				}
			}
		}, new Runnable() {
			@Override
			public void run() {
				clean.run();
				if (end != null) {
					end.run();
				}
			}
		});
		endManagers.put(instanceId, endManager);
		
		return endManager;
	}

	@Override
	public void prepare(final String script, final Failable fail, final Runnable end) {
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				EndManager endManager = endManager(null, fail, end);
				endManager.inc();
				try {
					StringBuilder scriptBuilder = new StringBuilder();
					scriptBuilder.append("__$instanceId = '" + endManager.instanceId + "';");
					scriptBuilder.append(script);
					
					String s = scriptBuilder.toString();
					try {
						scriptEngine.eval(s);
					} catch (ScriptException se) {
						endManager.fail(new IOException(se));
					}
				} finally {
					endManager.dec();
				}
			}
		});
	}

	@Override
	public Engine engine() {
		return new Engine() {
			private final Map<String, SyncScriptFunction> syncFunctions = new LinkedHashMap<>();
			private final Map<String, AsyncScriptFunction> asyncFunctions = new LinkedHashMap<>();
			@Override
			public void register(String function, SyncScriptFunction syncFunction) {
				syncFunctions.put(function, syncFunction);
			}
			@Override
			public void register(String function, AsyncScriptFunction asyncFunction) {
				asyncFunctions.put(function, asyncFunction);
			}
			
			@Override
			public void eval(final String script, final Failable fail, final Runnable end) {
				executorService.execute(new Runnable() {
					@Override
					public void run() {
						final List<String> bindingsToRemove = new LinkedList<>();
						EndManager endManager = endManager(bindingsToRemove, fail, end);
						endManager.inc();
						try {
							String closureId = String.valueOf(nextUnicityId);
							nextUnicityId++;
	
							StringBuilder scriptBuilder = new StringBuilder();
							scriptBuilder.append("__$instanceId = '" + endManager.instanceId + "';");
							scriptBuilder.append("var __$closure" + closureId + " = function() {");
	
							for (Map.Entry<String, SyncScriptFunction> e : syncFunctions.entrySet()) {
								String function = e.getKey();
								SyncScriptFunction syncFunction = e.getValue();
								
								String id = String.valueOf(nextUnicityId);
								nextUnicityId++;
								
								String functionObjectVar = "__$function" + id;
								scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).put(functionObjectVar, new SyncInternal(syncFunction));
								bindingsToRemove.add(functionObjectVar);
								
								if (USE_TO_STRING) {
									scriptBuilder.append(""
												+ "var " + function + " = function(p) {"
													+ "var r = " + functionObjectVar + ".call(JSON.stringify(p));"
													+ "if (r == null) {"
														+ "return null;"
													+ "}"
													+ "return JSON.parse(r);"
												+ "};"
											);
								} else {
									scriptBuilder.append(""
												+ "var " + function + " = function(p) {"
													+ "var r = " + functionObjectVar + ".call(__$convertFrom(p));"
													+ "if (r == null) {"
														+ "return null;"
													+ "}"
													+ "return __$convertTo(r);"
												+ "};"
											);
								}
							}
	
							for (Map.Entry<String, AsyncScriptFunction> e : asyncFunctions.entrySet()) {
								String function = e.getKey();
								AsyncScriptFunction asyncFunction = e.getValue();
								
								String id = String.valueOf(nextUnicityId);
								nextUnicityId++;
								
								String functionObjectVar = "__$function" + id;
								scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).put(functionObjectVar, new AsyncInternal(asyncFunction));
								bindingsToRemove.add(functionObjectVar);

								if (USE_TO_STRING) {
									scriptBuilder.append(""
												+ "var " + function + " = function(p, callback) {"
													+ "var callbackId = '" + function + "' + __$nextUnicityId;"
													+ "__$nextUnicityId++;"
													+ "__$callbacks[callbackId] = callback;"
													+ functionObjectVar + ".call(__$instanceId, JSON.stringify(p), callbackId);"
												+ "};"
											);
								} else {
									scriptBuilder.append(""
												+ "var " + function + " = function(p, callback) {"
													+ "var callbackId = '" + function + "' + __$nextUnicityId;"
													+ "__$nextUnicityId++;"
													+ "__$callbacks[callbackId] = callback;"
													+ functionObjectVar + ".call(__$instanceId, __$convertFrom(p), callbackId);"
												+ "};"
											);
								}
							}
							
							scriptBuilder.append(script);
							scriptBuilder.append(""
									+ "};"
									+ "__$closure" + closureId + "();"
									+ "__$closure" + closureId + " = null;" //TODO check if required in Java7
									+ "__$closure" + closureId + " = undefined;" //TODO check if release from mem
								);
							
							String s = scriptBuilder.toString();
							try {
								scriptEngine.eval(s);
							} catch (ScriptException se) {
								endManager.fail(new IOException(se));
							}
						} finally {
							endManager.dec();
						}
					}
				});
			}
		};
	}

	// Must be be public to be called from javascript
	public static JsonElement jsonString(String b) {
		return new JsonPrimitive(b);
	}
	// Must be be public to be called from javascript
	public static JsonElement jsonNumber(Number b) {
		return new JsonPrimitive(b);
	}
	// Must be be public to be called from javascript
	public static JsonElement jsonBoolean(boolean b) {
		return new JsonPrimitive(b);
	}
	
}
