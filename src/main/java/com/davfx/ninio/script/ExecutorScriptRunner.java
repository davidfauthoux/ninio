package com.davfx.ninio.script;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

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
	
	private static final boolean ENGINE_PREPARED = CONFIG.getBoolean("script.prepared");
	private static final String ENGINE_NAME = CONFIG.getString("script.engine");
	static {
		LOGGER.debug("Engine: {}", ENGINE_NAME);
	}
	public static final String CALL_FUNCTION_NAME = CONFIG.getString("script.functions.call");
	public static final String UNICITY_PREFIX = CONFIG.getString("script.functions.unicity.prefix");
	
	private final ReusableScriptEngine scriptEngine;
	private ReusableScriptEngine.EngineGenerator preparedScriptEngine = null;
	private final ExecutorService executorService = Executors.newSingleThreadExecutor(new ClassThreadFactory(ExecutorScriptRunner.class));
	private final Mutable<Long> nextCallbackFunctionSuffix = new Mutable<Long>(0L);

	public ExecutorScriptRunner() {
		super(ExecutorScriptRunner.class);
		
		final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
		
		if (ENGINE_PREPARED) {
			ScriptEngine engine = scriptEngineManager.getEngineByName(ENGINE_NAME);
			if (engine == null) {
				throw new IllegalArgumentException("Bad engine: " + ENGINE_NAME);
			}
			//%% } else {
			LOGGER.debug("Script engine {}/{}", engine.getFactory().getEngineName(), engine.getFactory().getEngineVersion());

			scriptEngine = new SharingReusableScriptEngine(engine);
		} else {
			scriptEngine = new SimpleReusableScriptEngine(new SimpleReusableScriptEngine.ScriptEngineFactory() {
				@Override
				public ScriptEngine engine() {
					return scriptEngineManager.getEngineByName(ENGINE_NAME);
				}
			});
		}
	}
	
	@Override
	public void close() {
		LOGGER.debug("Script engine closed");
		executorService.shutdown();
	}
	
	private static final class EndManager {
		private int count = 0;
		private Runnable end;
		private Failable fail;
		public EndManager(Failable fail, Runnable end) {
			this.end = end;
		}
		public void fail(IOException e) {
			end = null;
			if (fail != null) {
				Failable f = fail;
				fail = null;
				f.failed(e);
			}
		}
		public void inc() {
			count++;
		}
		public void dec() {
			count--;
			if (count == 0) {
				// engine.setBindings(new SimpleBindings(), ScriptContext.ENGINE_SCOPE);
				fail = null;
				if (end != null) {
					Runnable e = end;
					end = null;
					e.run();
				}
			}
		}
	}
	
	// Must be be public to be called from javascript
	public static final class FromScriptUsingConvert extends CheckAllocationObject {
		private final ReusableScriptEngine.Engine reusableEngine;
		private final ExecutorService executorService;
		private final Mutable<Long> nextUnicitySuffix;
		private final EndManager endManager;
		private final AsyncScriptFunction<JsonElement> asyncFunction;
		private final SyncScriptFunction<JsonElement> syncFunction;

		private FromScriptUsingConvert(ReusableScriptEngine.Engine reusableEngine, ExecutorService executorService, Mutable<Long> nextCallbackFunctionSuffix, EndManager endManager, AsyncScriptFunction<JsonElement> asyncFunction, SyncScriptFunction<JsonElement> syncFunction) {
			super(FromScriptUsingConvert.class);
			this.reusableEngine = reusableEngine;
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
								long suffix;
								
								suffix = nextUnicitySuffix.get();
								String callbackVar = UNICITY_PREFIX + "callback" + suffix;
								nextUnicitySuffix.set(suffix + 1);
								
								reusableEngine.bind(callbackVar, callback);
	
								suffix = nextUnicitySuffix.get();
								String parameterVar = UNICITY_PREFIX + "parameter" + suffix;
								nextUnicitySuffix.set(suffix + 1);
								
								reusableEngine.bind(parameterVar, response);
	
								String callbackScript = callbackVar + "(" + UNICITY_PREFIX + "convertTo(" + parameterVar + "));";
								try {
									LOGGER.trace("Executing callback: {}", callbackScript);
									reusableEngine.eval(callbackScript);
								} catch (Exception e) {
									LOGGER.error("Script error in: {}", callbackScript, e);
									endManager.fail(new IOException(e));
								}
								
								reusableEngine.unbind(callbackVar);
								reusableEngine.unbind(parameterVar);
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
	
	public static final class FromScriptUsingToString extends CheckAllocationObject {
		private final ReusableScriptEngine.Engine reusableEngine;
		private final ExecutorService executorService;
		private final Mutable<Long> nextUnicitySuffix;
		private final EndManager endManager;
		private final AsyncScriptFunction<JsonElement> asyncFunction;
		private final SyncScriptFunction<JsonElement> syncFunction;
		
		private FromScriptUsingToString(ReusableScriptEngine.Engine reusableEngine, ExecutorService executorService, Mutable<Long> nextCallbackFunctionSuffix, EndManager endManager, AsyncScriptFunction<JsonElement> asyncFunction, SyncScriptFunction<JsonElement> syncFunction) {
			super(FromScriptUsingToString.class);
			this.reusableEngine = reusableEngine;
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
								long suffix;
								
								suffix = nextUnicitySuffix.get();
								String callbackVar = UNICITY_PREFIX + "callback" + suffix;
								nextUnicitySuffix.set(suffix + 1);
								
								reusableEngine.bind(callbackVar, callback);
	
								String callbackScript = callbackVar + "(" + response.toString() + ");";
								try {
									LOGGER.trace("Executing callback: {}", callbackScript);
									reusableEngine.eval(callbackScript);
								} catch (Exception e) {
									LOGGER.error("Script error in: {}", callbackScript, e);
									endManager.fail(new IOException(e));
								}
								
								reusableEngine.unbind(callbackVar);
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
				EndManager endManager = new EndManager(fail, null);
				endManager.inc();
				try {
					String callFunctions;
					
					if (USE_TO_STRING) {
						callFunctions = "var " + CALL_FUNCTION_NAME + " = function(parameter, callback) { return JSON.parse(" + UNICITY_PREFIX + "call.call(JSON.stringify(parameter), callback || null)); }; ";
					} else {
						try {
							scriptEngine.prepare("var " + UNICITY_PREFIX + "convertFrom = function(o) {"
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
								+ "};");
							scriptEngine.prepare("var " + UNICITY_PREFIX + "convertTo = function(o) {"
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
								+ "};");
						} catch (Exception e) {
							LOGGER.error("Script error", e);
							endManager.fail(new IOException(e));
							return;
						}
	
						callFunctions = "var " + CALL_FUNCTION_NAME + " = function(parameter, callback) { return " + UNICITY_PREFIX + "convertTo(" + UNICITY_PREFIX + "call.call(" + UNICITY_PREFIX + "convertFrom(parameter), callback || null)); }; ";
					}
	
					callFunctions = "var " + UNICITY_PREFIX + "call = null;" + callFunctions;
					
					try {
						scriptEngine.prepare(ScriptUtils.functions());
						// LOGGER.debug("Executing functions: {}", callFunctions);
						scriptEngine.prepare(callFunctions);
					} catch (Exception e) {
						LOGGER.error("Script error", e);
						endManager.fail(new IOException(e));
						return;
					}
	
					int k = 0;
					for (String s : script) {
						// LOGGER.debug("Executing script {}", s);
						long t = System.currentTimeMillis();
						try {
							scriptEngine.prepare(s);
						} catch (Exception e) {
							LOGGER.error("Script error in: {}", s, e);
							endManager.fail(new IOException(e));
							return;
						}
						t = System.currentTimeMillis() - t;
						LOGGER.trace("Prepare script #{} executed in {} ms\n{}", k, t, s);
						k++;
					}
					
					try {
						preparedScriptEngine = scriptEngine.finish();
					} catch (Exception e) {
						LOGGER.error("Script error", e);
						endManager.fail(new IOException(e));
						return;
					}

				} finally {
					endManager.dec();
				}
			} 
		});
	}
	
	@Override
	public void eval(final Iterable<String> script, final Failable fail, final Runnable end, final AsyncScriptFunction<JsonElement> asyncFunction, final SyncScriptFunction<JsonElement> syncFunction) {
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				EndManager endManager = new EndManager(fail, end);
				endManager.inc();
				try {
					if (preparedScriptEngine == null) {
						endManager.fail(new IOException("Error in prepare"));
						return;
					}
					
					ReusableScriptEngine.Engine reusableEngine = preparedScriptEngine.get();
	
					if (USE_TO_STRING) {
						reusableEngine.bind(UNICITY_PREFIX + "call", new FromScriptUsingToString(reusableEngine, executorService, nextCallbackFunctionSuffix, endManager, asyncFunction, syncFunction));
					} else {
						reusableEngine.bind(UNICITY_PREFIX + "call", new FromScriptUsingConvert(reusableEngine, executorService, nextCallbackFunctionSuffix, endManager, asyncFunction, syncFunction));
					}

					int k = 0;
					for (String s : script) {
						// LOGGER.debug("Executing script {} with binding: {}", s, bindings.get(UNICITY_PREFIX + "call"));
						long t = System.currentTimeMillis();
						try {
							reusableEngine.eval(s);
						} catch (Exception e) {
							LOGGER.error("Script error in: {}", s, e);
							endManager.fail(new IOException(e));
							return;
						}
						t = System.currentTimeMillis() - t;
						LOGGER.trace("Script #{} executed in {} ms\n{}", k, t, s);
						k++;
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
