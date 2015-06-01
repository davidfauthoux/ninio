package com.davfx.ninio.script;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.ClassThreadFactory;
import com.davfx.ninio.common.Failable;
import com.davfx.ninio.util.CheckAllocationObject;
import com.davfx.util.ConfigUtils;
import com.davfx.util.Mutable;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

public final class ExecutorScriptRunner extends CheckAllocationObject implements ScriptRunner<JsonElement>, AutoCloseable {
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
	private Bindings initialBindings = null;
	private final ExecutorService executorService = Executors.newSingleThreadExecutor(new ClassThreadFactory(ExecutorScriptRunner.class));
	private final Mutable<Long> nextCallbackFunctionSuffix = new Mutable<Long>(0L);

	public ExecutorScriptRunner() {
		super(ExecutorScriptRunner.class);
		
		ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
		scriptEngine = scriptEngineManager.getEngineByName(ENGINE_NAME);
		if (scriptEngine == null) {
			throw new IllegalArgumentException("Bad engine: " + ENGINE_NAME);
		}
		//%% } else {
		LOGGER.debug("Script engine {}/{}", scriptEngine.getFactory().getEngineName(), scriptEngine.getFactory().getEngineVersion());
	}
	
	@Override
	public void close() {
		LOGGER.debug("Script engine closed");
		executorService.shutdown();
	}
	
	private static final class EndManager {
		private int count = 0;
		private final Runnable end;
		public EndManager(Runnable end) {
			this.end = end;
		}
		public void inc() {
			count++;
		}
		public void dec() {
			count--;
			if (count == 0) {
				// engine.setBindings(new SimpleBindings(), ScriptContext.ENGINE_SCOPE);
				end.run();
			}
		}
	}
	
	// Must be be public to be called from javascript
	public static final class FromScriptUsingConvert extends CheckAllocationObject {
		private final ScriptEngine scriptEngine;
		private final Bindings bindings;
		private final ExecutorService executorService;
		private final Mutable<Long> nextUnicitySuffix;
		private final Failable fail;
		private final EndManager endManager;
		private final AsyncScriptFunction<JsonElement> asyncFunction;
		private final SyncScriptFunction<JsonElement> syncFunction;

		private FromScriptUsingConvert(ScriptEngine scriptEngine, Bindings bindings, ExecutorService executorService, Mutable<Long> nextCallbackFunctionSuffix, Failable fail, EndManager endManager, AsyncScriptFunction<JsonElement> asyncFunction, SyncScriptFunction<JsonElement> syncFunction) {
			super(FromScriptUsingConvert.class);
			this.scriptEngine = scriptEngine;
			this.bindings = bindings;
			this.fail = fail;
			this.endManager = endManager;
			this.executorService = executorService;
			this.nextUnicitySuffix = nextCallbackFunctionSuffix;
			this.asyncFunction = asyncFunction;
			this.syncFunction = syncFunction;
		}

		public JsonElement call(JsonElement fromScriptParameterAsJson, final Object callback) throws ScriptException {
			if (callback == null) {
				JsonElement r = syncFunction.call(fromScriptParameterAsJson);
				if (r == null) {
					return null;
				}
				return r;
			}
			endManager.inc();
			asyncFunction.call(fromScriptParameterAsJson, new AsyncScriptFunction.Callback<JsonElement>() {
				@Override
				public void handle(final JsonElement response) {
					executorService.execute(new Runnable() {
						@Override
						public void run() {
							try {
								//%%% Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
								long suffix;
								
								suffix = nextUnicitySuffix.get();
								String callbackVar = UNICITY_PREFIX + "callback" + suffix;
								nextUnicitySuffix.set(suffix + 1);
								
								bindings.put(callbackVar, callback);
	
								suffix = nextUnicitySuffix.get();
								String parameterVar = UNICITY_PREFIX + "parameter" + suffix;
								nextUnicitySuffix.set(suffix + 1);
								
								bindings.put(parameterVar, response);
	
								// scriptEngine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
								
								String callbackScript = callbackVar + "(" + UNICITY_PREFIX + "convertTo(" + parameterVar + "));";
								try {
									LOGGER.trace("Executing callback: {}", callbackScript);
									scriptEngine.eval(callbackScript, bindings);
								} catch (Exception e) {
									LOGGER.error("Script error in: {}", callbackScript, e);
									if (fail != null) {
										fail.failed(new IOException(e));
									}
								}
								
								bindings.remove(callbackVar);
								bindings.remove(parameterVar);
							} finally {
								endManager.dec();
							}
						}
					});
				}
				/*%%%%%%%%%%%%%%%
				@Override
				public void close() {
					executorService.execute(new Runnable() {
						@Override
						public void run() {
							Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
							
							long suffix;
							
							suffix = nextUnicitySuffix.get();
							String callbackVar = UNICITY_PREFIX + "callback" + suffix;
							nextUnicitySuffix.set(suffix + 1);
							
							bindings.put(callbackVar, callback);

							String callbackScript = callbackVar + "();";
							try {
								LOGGER.trace("Executing callback: {}", callbackScript);
								scriptEngine.eval(callbackScript);
							} catch (Exception e) {
								LOGGER.error("Script error in: {}", callbackScript, e);
								if (fail != null) {
									fail.failed(new IOException(e));
								}
							}
							
							bindings.remove(callbackVar);
						}
					});
				}
				*/
			});
			return null;
		}
	}
	
	public static final class FromScriptUsingToString extends CheckAllocationObject {
		private final ScriptEngine scriptEngine;
		private final Bindings bindings;
		private final ExecutorService executorService;
		private final Mutable<Long> nextUnicitySuffix;
		private final Failable fail;
		private final EndManager endManager;
		private final AsyncScriptFunction<JsonElement> asyncFunction;
		private final SyncScriptFunction<JsonElement> syncFunction;
		
		private FromScriptUsingToString(ScriptEngine scriptEngine, Bindings bindings, ExecutorService executorService, Mutable<Long> nextCallbackFunctionSuffix, Failable fail, EndManager endManager, AsyncScriptFunction<JsonElement> asyncFunction, SyncScriptFunction<JsonElement> syncFunction) {
			super(FromScriptUsingToString.class);
			this.scriptEngine = scriptEngine;
			this.bindings = bindings;
			this.fail = fail;
			this.endManager = endManager;
			this.executorService = executorService;
			this.nextUnicitySuffix = nextCallbackFunctionSuffix;
			this.asyncFunction = asyncFunction;
			this.syncFunction = syncFunction;
		}

		public String call(String fromScriptParameter, final Object callback) throws ScriptException {
			JsonElement fromScriptParameterAsJson = new JsonParser().parse(fromScriptParameter);
			if (callback == null) {
				JsonElement r = syncFunction.call(fromScriptParameterAsJson);
				if (r == null) {
					return null;
				}
				return r.toString();
			}
			endManager.inc();
			asyncFunction.call(fromScriptParameterAsJson, new AsyncScriptFunction.Callback<JsonElement>() {
				@Override
				public void handle(final JsonElement response) {
					executorService.execute(new Runnable() {
						@Override
						public void run() {
							try {
								//%%%%%% Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
								long suffix;
								
								suffix = nextUnicitySuffix.get();
								String callbackVar = UNICITY_PREFIX + "callback" + suffix;
								nextUnicitySuffix.set(suffix + 1);
								
								bindings.put(callbackVar, callback);
	
								// scriptEngine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
								
								String callbackScript = callbackVar + "(" + response.toString() + ");";
								try {
									LOGGER.trace("Executing callback: {}", callbackScript);
									scriptEngine.eval(callbackScript, bindings);
								} catch (Exception e) {
									LOGGER.error("Script error in: {}", callbackScript, e);
									if (fail != null) {
										fail.failed(new IOException(e));
									}
								}
								
								bindings.remove(callbackVar);
							} finally {
								endManager.dec();
							}
						}
					});
				}
			});
			return null;
		}
	}

	@Override
	public void prepare(final Iterable<String> script, final Failable fail) {
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				initialBindings = null;

				/*%%
				if (scriptEngine == null) {
					if (fail != null) {
						fail.failed(new IOException("Bad engine"));
					}
					return;
				}
				*/

				//%%%%%%%%% Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
				Bindings bindings = new CheckAllocationSimpleBindings();
				// scriptEngine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

				String callFunctions;
				
				if (USE_TO_STRING) {
					callFunctions = "var " + CALL_FUNCTION_NAME + " = function(parameter, callback) { return JSON.parse(" + UNICITY_PREFIX + "call['$'].call(JSON.stringify(parameter), callback || null)); }; ";
				} else {
					try {
						scriptEngine.eval("var " + UNICITY_PREFIX + "convertFrom = function(o) {"
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
							+ "};", bindings);
						scriptEngine.eval("var " + UNICITY_PREFIX + "convertTo = function(o) {"
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
										+ "return oo.getAsNumber();"
									+ "}"
									+ "if (oo.isBoolean()) {"
										+ "return oo.getAsBoolean();"
									+ "}"
									+ "return null;"
								+ "}"
								+ "return null;"
							+ "};", bindings);
					} catch (Exception e) {
						LOGGER.error("Script error", e);
						if (fail != null) {
							fail.failed(new IOException(e));
						}
						return;
					}

					callFunctions = "var " + CALL_FUNCTION_NAME + " = function(parameter, callback) { return " + UNICITY_PREFIX + "convertTo(" + UNICITY_PREFIX + "call['$'].call(" + UNICITY_PREFIX + "convertFrom(parameter), callback || null)); }; ";
				}

				callFunctions = "var " + UNICITY_PREFIX + "call = {'$':undefined};" + callFunctions;
				
				try {
					scriptEngine.eval(ScriptUtils.functions(), bindings);
					// LOGGER.debug("Executing functions: {}", callFunctions);
					scriptEngine.eval(callFunctions, bindings);
					int k = 0;
					for (String s : script) {
						// LOGGER.debug("Executing script {}", s);
						long t = System.currentTimeMillis();
						scriptEngine.eval(s, bindings);
						t = System.currentTimeMillis() - t;
						LOGGER.debug("Prepare script #{} executed in {} ms\n{}", k, t, s);
						k++;
					}
				} catch (Exception e) {
					LOGGER.error("Script error", e);
					if (fail != null) {
						fail.failed(new IOException(e));
					}
					return;
				}
				
				initialBindings = bindings;
			}
		});
	}
	
	private static final class CheckAllocationSimpleBindings extends SimpleBindings {
		@SuppressWarnings("unused")
		private CheckAllocationObject check = new CheckAllocationObject(CheckAllocationSimpleBindings.class);
		public CheckAllocationSimpleBindings() {
		}
	}

	@Override
	public void eval(final Iterable<String> script, final Failable fail, final Runnable end, final AsyncScriptFunction<JsonElement> asyncFunction, final SyncScriptFunction<JsonElement> syncFunction) {
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				EndManager endManager = new EndManager(end);
				endManager.inc();
				try {
					/*%%
					if (scriptEngine == null) {
						if (fail != null) {
							fail.failed(new IOException("Bad engine"));
						}
						return;
					}
					*/
	
					Bindings bindings = new CheckAllocationSimpleBindings();
					bindings.putAll(initialBindings);
	
					String subCallVar = UNICITY_PREFIX + "call$";
					
					if (USE_TO_STRING) {
						bindings.put(subCallVar, new FromScriptUsingToString(scriptEngine, bindings, executorService, nextCallbackFunctionSuffix, fail, endManager, asyncFunction, syncFunction));
					} else {
						bindings.put(subCallVar, new FromScriptUsingConvert(scriptEngine, bindings, executorService, nextCallbackFunctionSuffix, fail, endManager, asyncFunction, syncFunction));
					}

					// scriptEngine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

					try {
						scriptEngine.eval(UNICITY_PREFIX + "call['$'] = " + subCallVar + ";", bindings); // This re-referencing is required because functions are defined in prepare (before bindings.put(call))
						int k = 0;
						for (String s : script) {
							// LOGGER.debug("Executing script {} with binding: {}", s, bindings.get(UNICITY_PREFIX + "call"));
							long t = System.currentTimeMillis();
							scriptEngine.eval(s, bindings);
							t = System.currentTimeMillis() - t;
							LOGGER.debug("Script #{} executed in {} ms\n{}", k, t, s);
							k++;
						}
					} catch (Exception e) {
						LOGGER.error("Script error", e);
						if (fail != null) {
							fail.failed(new IOException(e));
						}
					}
					
				} finally {
					endManager.dec();
				}
			}
		});
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
	
	/*%%%
	public static JsonElement fromScript(Object o) {
		System.out.println(o);
		return null;
	}
	public static void print(JsonElement o) {
		System.out.println("---> " + o);
	}
	
	public static void main(String[] args) throws Exception {
		ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
		ScriptEngine scriptEngine = scriptEngineManager.getEngineByName("JavaScript");
		scriptEngine.eval("var foo = function(o) {"
				+ "if (o instanceof Array) {"
					+ "var p = new com.google.gson.JsonArray();"
					+ "for (k in o) {"
						+ "p.add(foo(o[k]));"
					+ "}"
					+ "return p;"
				+ "}"
				+ "if (o instanceof Object) {"
					+ "var p = new com.google.gson.JsonObject();"
					+ "for (k in o) {"
						+ "p.add(k, foo(o[k]));"
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
				+ "return null;"
			+ "};");
		scriptEngine.eval("var foo2 = function(o) {"
				+ "if (o.isJsonObject()) {"
					+ "var i = o.entrySet().iterator();"
					+ "var p = {};"
					+ "while (i.hasNext()) {"
						+ "var e = i.next();"
						+ "p[e.getKey()] = foo2(e.getValue());"
					+ "}"
					+ "return p;"
				+ "}"
				+ "if (o.isJsonPrimitive()) {"
					+ "var oo = o.getAsJsonPrimitive();"
					+ "if (oo.isString()) {"
						+ "return '' + oo.getAsString().toString();"
					+ "}"
					+ "if (oo.isNumber()) {"
						+ "return oo.getAsNumber();"
					+ "}"
					+ "if (oo.isBoolean()) {"
						+ "return oo.getAsBoolean();"
					+ "}"
					+ "return null;"
				+ "}"
				+ "return null;"
			+ "};");
		

		long t = System.currentTimeMillis();
		for (int i = 0; i < 1000; i++) {
			scriptEngine.eval("foo2(foo({'a':'aa', 'b':{'c':'cc','d':'dd',e:4.5,f:true,g:500}}));");
		}
		t = System.currentTimeMillis() - t;
		System.out.println(t);
		
		t = System.currentTimeMillis();
		for (int i = 0; i < 1000; i++) {
			scriptEngine.eval("JSON.parse(JSON.stringify({'a':'aa', 'b':{'c':'cc','d':'dd',e:4.5,f:true,g:500}}));");
		}
		t = System.currentTimeMillis() - t;
		System.out.println(t);
		
		Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
		
		t = System.currentTimeMillis();
		for (int i = 0; i < 100; i++) {
			JsonObject json = new JsonObject();
			json.add("a", new JsonPrimitive("Uploa'ding: https://oss.sonatype.org:443/service'/local/staging/deplo\"yByRepositoryI'd/comdavfx-1039/com/davfx/ninio/0.0.17/ninio-0.0.17-sources.jar.asc"));
			json.add("b", new JsonPrimitive("bb"));
			json.add("c", new JsonPrimitive("cc"));
			for (int ii = 0; ii < 500; ii++) {
				JsonObject j = new JsonObject();
				j.add("a", new JsonPrimitive("Uploa'ding: https://oss.sonatype.org:443/service'/local/staging/deplo\"yByRepositoryI'd/comdavfx-1039/com/davfx/ninio/0.0.17/ninio-0.0.17-sources.jar.asc"));
				j.add("b", new JsonPrimitive("bb"));
				j.add("c", new JsonPrimitive("cc"));
				j.add("d", new JsonPrimitive(666.777));
				j.add("e", new JsonPrimitive(true));
				j.add("f", new JsonPrimitive(false));
				json.add("ii" + ii, j);
			}
			scriptEngine.eval("JSON.parse(JSON.stringify(" + json.toString() + "))");
		}
		t = System.currentTimeMillis() - t;
		System.out.println(t);
		
		t = System.currentTimeMillis();
		for (int i = 0; i < 100; i++) {
			JsonObject json = new JsonObject();
			json.add("a", new JsonPrimitive("Uploa'ding: https://oss.sonatype.org:443/service'/local/staging/deplo\"yByRepositoryI'd/comdavfx-1039/com/davfx/ninio/0.0.17/ninio-0.0.17-sources.jar.asc"));
			json.add("b", new JsonPrimitive("bb"));
			json.add("c", new JsonPrimitive("cc"));
			for (int ii = 0; ii < 500; ii++) {
				JsonObject j = new JsonObject();
				j.add("a", new JsonPrimitive("Uploa'ding: https://oss.sonatype.org:443/service'/local/staging/deplo\"yByRepositoryI'd/comdavfx-1039/com/davfx/ninio/0.0.17/ninio-0.0.17-sources.jar.asc"));
				j.add("b", new JsonPrimitive("bb"));
				j.add("c", new JsonPrimitive("cc"));
				j.add("d", new JsonPrimitive(666.777));
				j.add("e", new JsonPrimitive(true));
				j.add("f", new JsonPrimitive(false));
				json.add("ii" + ii, j);
			}
			bindings.put("json", json);
			scriptEngine.eval("foo(foo2(json));");
		}
		t = System.currentTimeMillis() - t;
		System.out.println(t);
		
		
		scriptEngine.eval(ExecutorScriptRunner.class.getName() + ".print(foo({'a':'aa', 'b':{'c':'cc','d':'dd'}}));");
		scriptEngine.eval(ExecutorScriptRunner.class.getName() + ".print(foo('abc'));");
		scriptEngine.eval(ExecutorScriptRunner.class.getName() + ".print(foo(678));");
		scriptEngine.eval(ExecutorScriptRunner.class.getName() + ".print(foo({'a':'aa', 'b':{'c':'cc','d':123}}));");
		scriptEngine.eval(ExecutorScriptRunner.class.getName() + ".print(foo({'a':'aa', 'b':{'c':'cc','d':[ 'a1', 'a2' ]}}));");
		scriptEngine.eval(ExecutorScriptRunner.class.getName() + ".print(foo({'a':'aa', 'b':{'c':'cc','d':[ 'a1', 123, 456 ]}}));");
		scriptEngine.eval("java.lang.System.out.println(JSON.stringify(foo2(foo({'a':'aa', 'b':{'c':'cc','d':'dd',e:4.5,f:true,g:500}}))));");
		System.exit(0);
	}
	*/
}
