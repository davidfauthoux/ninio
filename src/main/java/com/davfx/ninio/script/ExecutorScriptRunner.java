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
	/*%%%%%%%
	public static void main(String[] args) throws Exception {
		ScriptRunner r = new ExecutorScriptRunner();
		ScriptRunner.Engine e = r.engine();
		e.register("echo", new SyncScriptFunction() {
			@Override
			public JsonElement call(JsonElement request) {
				JsonObject o = new JsonObject();
				o.add("request", request);
				o.add("response", new JsonPrimitive("This is an echo"));
				return o;
			}
		});
		e.eval("java.lang.System.out.println('ECHO ' + JSON.stringify(echo('aaa')));", null, null);
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
	}
		*/
	
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
	public static final String UNICITY_PREFIX = CONFIG.getString("script.functions.unicity.prefix");
	
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
					+ "var " + UNICITY_PREFIX + "nextUnicityId = 0;"
					+ "var " + UNICITY_PREFIX + "callbacks = {};"
					+ "var " + UNICITY_PREFIX + "instanceId = null;"
				);
			
			if (USE_TO_STRING) {
				scriptEngine.eval(""
						+ "var " + UNICITY_PREFIX + "convertFrom = JSON.stringify;"
						+ "var " + UNICITY_PREFIX + "convertTo = JSON.parse;"
					);
			} else {
				scriptEngine.eval(""
						+ "var " + UNICITY_PREFIX + "convertFrom = function(o) {"
							+ "if (o == null) {"
								+ "return null;"
							+ "}"
							+ "if (o instanceof Array) {"
								+ "var p = new com.google.gson.JsonArray();"
								+ "for (k in o) {"
									+ "p.add(" + UNICITY_PREFIX + "convertFrom(o[k]));"
								+ "}"
								+ "return p;"
							+ "}"
							+ "if (o instanceof Object) {"
								+ "var p = new com.google.gson.JsonObject();"
								+ "for (k in o) {"
									+ "p.add(k, " + UNICITY_PREFIX + "convertFrom(o[k]));"
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
					+ "var " + UNICITY_PREFIX + "convertTo = function(o) {"
						+ "if (o == null) {"
							+ "return null;"
						+ "}"
						+ "if (o.isJsonObject()) {"
							+ "var i = o.entrySet().iterator();"
							+ "var p = {};"
							+ "while (i.hasNext()) {"
								+ "var e = i.next();"
								+ "p[e.getKey()] = " + UNICITY_PREFIX + "convertTo(e.getValue());"
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
		public Object call(Object requestAsObject) {
			JsonElement request;
			if (requestAsObject == null) {
				request = null;
			} else {
				if (USE_TO_STRING) {
					request = new JsonParser().parse((String) requestAsObject);
				} else {
					request = (JsonElement) requestAsObject;
				}
			}
			
			JsonElement response = syncFunction.call(request);
			if (response == null) {
				return null;
			}
			
			if (USE_TO_STRING) {
				return response.toString();
			} else {
				return response;
			}
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
			if (requestAsObject == null) {
				request = null;
			} else {
				if (USE_TO_STRING) {
					request = new JsonParser().parse((String) requestAsObject);
				} else {
					request = (JsonElement) requestAsObject;
				}
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
												+ "var " + UNICITY_PREFIX + "f = " + UNICITY_PREFIX + "callbacks['" + callbackId + "'];"
												+ "delete " + UNICITY_PREFIX + "callbacks['" + callbackId + "'];"
												+ "if (" + UNICITY_PREFIX + "f) " + UNICITY_PREFIX + "f(" + ((response == null) ? "null" : response.toString()) + ");"
												+ UNICITY_PREFIX + "f = null;" // Memsafe null-set
												+ UNICITY_PREFIX + "f = undefined;"
											);
									} else {
										if (response == null) {
											scriptEngine.eval("var " + UNICITY_PREFIX + "r = null;");
										} else {
											String id = String.valueOf(nextUnicityId);
											nextUnicityId++;

											scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).put(UNICITY_PREFIX + "response" + id, response);
											try {
												scriptEngine.eval("var " + UNICITY_PREFIX + "r = " + UNICITY_PREFIX + "convertTo(" + UNICITY_PREFIX + "response" + id + ");");
											} finally {
												scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).put(UNICITY_PREFIX + "response" + id, null); // Memsafe null-set
												scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).remove(UNICITY_PREFIX + "response" + id);
											}
										}
										
										scriptEngine.eval(""
												+ "var " + UNICITY_PREFIX + "f = " + UNICITY_PREFIX + "callbacks['" + callbackId + "'];"
												+ "delete " + UNICITY_PREFIX + "callbacks['" + callbackId + "'];"
												+ "if (" + UNICITY_PREFIX + "f) " + UNICITY_PREFIX + "f(" + UNICITY_PREFIX + "r);"
												+ UNICITY_PREFIX + "r = null;" // Memsafe null-set
												+ UNICITY_PREFIX + "r = undefined;"
												+ UNICITY_PREFIX + "f = null;" // Memsafe null-set
												+ UNICITY_PREFIX + "f = undefined;"
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
				
				String functionObjectVar = UNICITY_PREFIX + "function_" + function + id;
				scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).put(functionObjectVar, new SyncInternal(syncFunction));
				
				try {
					scriptEngine.eval(""
							+ "var " + function + " = function(p) {"
								+ "var q = null;"
								+ "if (p) {"
									+ "q = " + UNICITY_PREFIX + "convertFrom(p);"
								+ "}"
								+ "var r = " + functionObjectVar + ".call(q);"
								+ "if (r == null) {"
									+ "return null;"
								+ "}"
								+ "return " + UNICITY_PREFIX + "convertTo(r);"
							+ "};"
						);
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
				
				String functionObjectVar = UNICITY_PREFIX + "function_" + function + id;
				scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).put(functionObjectVar, new AsyncInternal(asyncFunction));
				
				try {
					scriptEngine.eval(""
							+ "var " + function + " = function(p, callback) {"
								+ "var q = null;"
								+ "if (p) {"
									+ "q = " + UNICITY_PREFIX + "convertFrom(p);"
								+ "}"
								+ "var callbackId = '" + function + "' + " + UNICITY_PREFIX + "nextUnicityId;"
								+ UNICITY_PREFIX + "nextUnicityId++;"
								+ UNICITY_PREFIX + "callbacks[callbackId] = callback;"
								+ functionObjectVar + ".call(" + UNICITY_PREFIX + "instanceId, q, callbackId);"
							+ "};"
						);
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
						scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).put(functionObjectVar, null); // Memsafe null-set
						scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).remove(functionObjectVar);
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
					scriptBuilder.append(UNICITY_PREFIX + "instanceId = '" + endManager.instanceId + "';");
					scriptBuilder.append(script);
					
					String s = scriptBuilder.toString();
					try {
						scriptEngine.eval(s);
					} catch (ScriptException se) {
						LOGGER.error("Script error: {}", s, se);
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
							scriptBuilder.append(UNICITY_PREFIX + "instanceId = '" + endManager.instanceId + "';");
							scriptBuilder.append("var " + UNICITY_PREFIX + "closure" + closureId + " = function() {");
	
							for (Map.Entry<String, SyncScriptFunction> e : syncFunctions.entrySet()) {
								String function = e.getKey();
								SyncScriptFunction syncFunction = e.getValue();
								
								String id = String.valueOf(nextUnicityId);
								nextUnicityId++;
								
								String functionObjectVar = UNICITY_PREFIX + "function_" + function + id;
								scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).put(functionObjectVar, new SyncInternal(syncFunction));
								bindingsToRemove.add(functionObjectVar);
								
								scriptBuilder.append(""
											+ "var " + function + " = function(p) {"
												+ "var q = null;"
												+ "if (p) {"
													+ "q = " + UNICITY_PREFIX + "convertFrom(p);"
												+ "}"
												+ "var r = " + functionObjectVar + ".call(q);"
												+ "if (r == null) {"
													+ "return null;"
												+ "}"
												+ "return " + UNICITY_PREFIX + "convertTo(r);"
											+ "};"
										);
							}
	
							for (Map.Entry<String, AsyncScriptFunction> e : asyncFunctions.entrySet()) {
								String function = e.getKey();
								AsyncScriptFunction asyncFunction = e.getValue();
								
								String id = String.valueOf(nextUnicityId);
								nextUnicityId++;
								
								String functionObjectVar = UNICITY_PREFIX + "function_" + function + id;
								scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE).put(functionObjectVar, new AsyncInternal(asyncFunction));
								bindingsToRemove.add(functionObjectVar);

								scriptBuilder.append(""
											+ "var " + function + " = function(p, callback) {"
												+ "var q = null;"
												+ "if (p) {"
													+ "q = " + UNICITY_PREFIX + "convertFrom(p);"
												+ "}"
												+ "var callbackId = '" + function + "' + " + UNICITY_PREFIX + "nextUnicityId;"
												+ UNICITY_PREFIX + "nextUnicityId++;"
												+ UNICITY_PREFIX + "callbacks[callbackId] = callback;"
												+ functionObjectVar + ".call(" + UNICITY_PREFIX + "instanceId, q, callbackId);"
											+ "};"
										);
							}
							
							scriptBuilder.append(script);
							scriptBuilder.append(""
									+ "};"
									+ UNICITY_PREFIX + "closure" + closureId + "();"
									+ UNICITY_PREFIX + "closure" + closureId + " = null;" // Memsafe null-set
									+ UNICITY_PREFIX + "closure" + closureId + " = undefined;"
								);
							
							String s = scriptBuilder.toString();
							try {
								scriptEngine.eval(s);
							} catch (ScriptException se) {
								LOGGER.error("Script error: {}", s, se);
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
