package com.davfx.ninio.script;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Failable;
import com.davfx.util.ConfigUtils;
import com.davfx.util.Mutable;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public final class ExecutorScriptRunner implements ScriptRunner<JsonElement>, AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorScriptRunner.class);
	
	public static final String CALL_FUNCTION_NAME = ConfigUtils.load(ExecutorScriptRunner.class).getString("script.functions.call");
	public static final String UNICITY_PREFIX = ConfigUtils.load(ExecutorScriptRunner.class).getString("script.functions.unicity.prefix");
	
	private final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
	private final ExecutorService executorService = Executors.newSingleThreadExecutor(new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			return new Thread(r, ExecutorScriptRunner.class.getSimpleName());
		}
	});
	private final Mutable<Long> nextCallbackFunctionSuffix = new Mutable<Long>(0L);

	public ExecutorScriptRunner() {
	}
	
	@Override
	public void close() {
		executorService.shutdown();
	}
	
	// Must be be public to be called from javascript
	public static final class FromScript {
		private final ScriptEngine scriptEngine;
		private final ExecutorService executorService;
		private final Mutable<Long> nextUnicitySuffix;
		private final Failable fail;
		private final AsyncScriptFunction<JsonElement> asyncFunction;
		private final SyncScriptFunction<JsonElement> syncFunction;
		
		private FromScript(ScriptEngine scriptEngine, ExecutorService executorService, Mutable<Long> nextCallbackFunctionSuffix, Failable fail, AsyncScriptFunction<JsonElement> asyncFunction, SyncScriptFunction<JsonElement> syncFunction) {
			this.scriptEngine = scriptEngine;
			this.fail = fail;
			this.executorService = executorService;
			this.nextUnicitySuffix = nextCallbackFunctionSuffix;
			this.asyncFunction = asyncFunction;
			this.syncFunction = syncFunction;
		}

		/*
		private JsonElement toJson(Object javascriptObject) {
			if (javascriptObject == null) {
				return null;
			}

			Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
			
			long suffix = nextUnicitySuffix.get();
			String parameterVar = UNICITY_PREFIX + "parameter" + suffix;
			nextUnicitySuffix.set(suffix + 1);
			
			bindings.put(parameterVar, javascriptObject);
			String r;
			try {
				r = (String) scriptEngine.eval("JSON.stringify(" + parameterVar + ")");
			} catch (Exception e) {
				LOGGER.error("Script error", e);
				r = null;
			}
			
			bindings.remove(parameterVar);
			if (r == null) {
				return null;
			}
			return new JsonParser().parse(r);
		}
		private Object fromJson(JsonElement jsonObject) {
			if (jsonObject == null) {
				return null;
			}
			
			Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);

			//%% long suffix = nextUnicitySuffix.get();
			String returnVar = UNICITY_PREFIX + "return"; //%% + suffix;
			//%% nextUnicitySuffix.set(suffix + 1);
			
			String s = jsonObject.toString();
			Object r;
			try {
				r = scriptEngine.eval(returnVar + " = " + s + ";");
			} catch (Exception e) {
				LOGGER.error("Script error", e);
				r = null;
			}
			bindings.remove(returnVar);
			return r;
		}
		*/
		
		public String call(String fromScriptParameter, final Object callback) throws ScriptException {
			JsonElement fromScriptParameterAsJson = new JsonParser().parse(fromScriptParameter); //%% toJson(fromScriptParameter);
			if (callback == null) {
				JsonElement r = syncFunction.call(fromScriptParameterAsJson);
				if (r == null) {
					return null;
				}
				return r.toString();
			}
			asyncFunction.call(fromScriptParameterAsJson, new AsyncScriptFunction.Callback<JsonElement>() {
				@Override
				public void handle(final JsonElement response) {
					executorService.execute(new Runnable() {
						@Override
						public void run() {
							Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
							
							long suffix;
							
							suffix = nextUnicitySuffix.get();
							String callbackVar = UNICITY_PREFIX + "callback" + suffix;
							nextUnicitySuffix.set(suffix + 1);
							
							bindings.put(callbackVar, callback);

							/*
							suffix = nextUnicitySuffix.get();
							String parameterVar = UNICITY_PREFIX + "parameter" + suffix;
							nextUnicitySuffix.set(suffix + 1);
							
							bindings.put(parameterVar, response.toString());
							*/

							String callbackScript = callbackVar + "(JSON.parse(" + new JsonPrimitive(response.toString()).toString() + "));";
							// String callbackScript = callbackVar + "(JSON.parse(" + parameterVar + "));";
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
							// bindings.remove(parameterVar);
						}
					});
				}
				/*
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
	
	@Override
	public void eval(final Iterable<String> script, final Failable fail, final AsyncScriptFunction<JsonElement> asyncFunction, final SyncScriptFunction<JsonElement> syncFunction) {
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				ScriptEngine scriptEngine = scriptEngineManager.getEngineByName("JavaScript");
				Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
				
				String callVar = UNICITY_PREFIX + "call";
				bindings.put(callVar, new FromScript(scriptEngine, executorService, nextCallbackFunctionSuffix, fail, asyncFunction, syncFunction));
				String callFunctions = "var " + CALL_FUNCTION_NAME + " = function(parameter, callback) { return JSON.parse(" + callVar + ".call(JSON.stringify(parameter), callback || null)); }; ";
				
				try {
					scriptEngine.eval(ScriptUtils.functions());
					LOGGER.trace("Executing functions: {}", callFunctions);
					scriptEngine.eval(callFunctions);
					for (String s : script) {
						LOGGER.trace("Executing: {}", s);
						scriptEngine.eval(s);
					}
				} catch (Exception e) {
					LOGGER.error("Script error", e);
					if (fail != null) {
						fail.failed(new IOException(e));
					}
				}
			}
		});
	}
}
