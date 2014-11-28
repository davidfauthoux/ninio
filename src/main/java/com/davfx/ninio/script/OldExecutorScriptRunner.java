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
import com.google.gson.JsonPrimitive;

@Deprecated
public final class OldExecutorScriptRunner implements ScriptRunner<String>, AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(OldExecutorScriptRunner.class);
	
	public static final String CALL_FUNCTION_NAME = ConfigUtils.load(OldExecutorScriptRunner.class).getString("script.functions.call");
	public static final String UNICITY_PREFIX = ConfigUtils.load(OldExecutorScriptRunner.class).getString("script.functions.unicity.prefix");
	
	private final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
	private final ExecutorService executorService = Executors.newSingleThreadExecutor(new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			return new Thread(r, OldExecutorScriptRunner.class.getSimpleName());
		}
	});
	private final Mutable<Long> nextUnicitySuffix = new Mutable<Long>(0L);

	public OldExecutorScriptRunner() {
	}
	
	@Override
	public void close() {
		executorService.shutdown();
	}
	
	// Must be be public to be called from javascript
	public static final class FromScript {
		private final ScriptEngine scriptEngine;
		private final ExecutorService executorService;
		private final Mutable<Long> nextCallbackFunctionSuffix;
		private final Failable fail;
		private final AsyncScriptFunction<String> asyncFunction;
		private final SyncScriptFunction<String> syncFunction;
		
		private FromScript(ScriptEngine scriptEngine, ExecutorService executorService, Mutable<Long> nextCallbackFunctionSuffix, Failable fail, AsyncScriptFunction<String> asyncFunction, SyncScriptFunction<String> syncFunction) {
			this.scriptEngine = scriptEngine;
			this.fail = fail;
			this.executorService = executorService;
			this.nextCallbackFunctionSuffix = nextCallbackFunctionSuffix;
			this.asyncFunction = asyncFunction;
			this.syncFunction = syncFunction;
		}

		public String call(String fromScriptParameter, final Object callback) throws ScriptException {
			if (callback == null) {
				return syncFunction.call(fromScriptParameter);
			}
			asyncFunction.call(fromScriptParameter, new AsyncScriptFunction.Callback<String>() {
				@Override
				public void handle(final String response) {
					executorService.execute(new Runnable() {
						@Override
						public void run() {
							Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
							
							long suffix = nextCallbackFunctionSuffix.get();
							String callbackVar = UNICITY_PREFIX + "callback" + suffix;
							nextCallbackFunctionSuffix.set(suffix + 1);
							
							bindings.put(callbackVar, callback);
							
							String fromFunctionParameter = new JsonPrimitive(response).toString();
							String callbackScript = callbackVar + "(" + fromFunctionParameter + ");";
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
				/*
				@Override
				public void close() {
					executorService.execute(new Runnable() {
						@Override
						public void run() {
							Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
							
							long suffix = nextCallbackFunctionSuffix.get();
							String callbackVar = UNICITY_PREFIX + "callback" + suffix;
							nextCallbackFunctionSuffix.set(suffix + 1);
							
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
	public void eval(final Iterable<String> script, final Failable fail, final AsyncScriptFunction<String> asyncFunction, final SyncScriptFunction<String> syncFunction) {
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				ScriptEngine scriptEngine = scriptEngineManager.getEngineByName("JavaScript");
				Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
				
				String callVar = UNICITY_PREFIX + "call";
				bindings.put(callVar, new FromScript(scriptEngine, executorService, nextUnicitySuffix, fail, asyncFunction, syncFunction));
				String callFunctions = "var " + CALL_FUNCTION_NAME + " = function(parameter, callback) { return " + callVar + ".call(parameter, callback || null); }; ";
				
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
