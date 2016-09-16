package com.davfx.ninio.script;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.script.com.sun.script.javascript.RhinoScriptEngineFactory;
import com.davfx.ninio.script.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.SerialExecutor;
import com.typesafe.config.Config;

//import lu.flier.script.V8ScriptEngineFactory;

public final class ExecutorScriptRunner implements ScriptRunner {
	private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorScriptRunner.class);

	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(ExecutorScriptRunner.class.getPackage().getName());
	private static final String UNICITY_PREFIX = CONFIG.getString("unicity.prefix");
	
	private ScriptEngine scriptEngine;
	private ConvertingBuilder builder;
	private final Executor executor = new SerialExecutor(ExecutorScriptRunner.class);

	public ExecutorScriptRunner() {
		this(null);
	}
	public ExecutorScriptRunner(final String engineProvider) {
		doExecute(new Runnable() {
			@Override
			public void run() {
				if (engineProvider == null) {
					scriptEngine = new ScriptEngineManager().getEngineByName("js");
				} else if (engineProvider.equals("rhino")) {
					scriptEngine = new RhinoScriptEngineFactory().getScriptEngine();
				//} else if (engineProvider.equals("jav8")) {
				//scriptEngine = new V8ScriptEngineFactory().getScriptEngine();
				} else {
					scriptEngine = new ScriptEngineManager().getEngineByName(engineProvider);
				}
				
				if (scriptEngine == null) {
					throw new IllegalArgumentException("Bad engine: " + engineProvider);
				}
				
				if (scriptEngine.getFactory().getEngineName().equals("Oracle Nashorn")) {
					LOGGER.debug("Using optimized converted");
					builder = new NashornConvertingBuilder();
				} else {
					builder = new JsonConvertingBuilder();
				}
				
				try {
					scriptEngine.eval("function " + UNICITY_PREFIX + "fromjs(o) {" + builder.fromJsScript("o") + "}");
					scriptEngine.eval("function " + UNICITY_PREFIX + "tojs(o) {" + builder.toJsScript("o") + "}");
				} catch (ScriptException e) {
					LOGGER.error("Could not prepare engine", e);
				}

				LOGGER.debug("Script engine {}/{}", scriptEngine.getFactory().getEngineName(), scriptEngine.getFactory().getEngineVersion());
			}
		});
	}
	
	private static final class EndManager {
		private int count = 0;
		private End end;
		private boolean ended = false;
		public EndManager(End end) {
			this.end = end;
		}
		public boolean isEnded() {
			return ended;
		}
		public void fail(Exception e) {
			ended = true;
			// LOGGER.error("Failed", e);
			End ee = end;
			end = null;
			if (ee != null) {
				ee.failed(e);
			}
		}
		public void inc() {
			count++;
		}
		public void dec() {
			count--;
			if (count == 0) {
				ended = true;
				End ee = end;
				end = null;
				if (ee != null) {
					ee.ended();
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private static Map<String, Object> cast(Object f) {
		return (Map<String, Object>) f;
	}
	
	// Must be be public to be called from javascript
	public final class SyncInternal {
		private final SyncScriptFunction syncFunction;
		private SyncInternal(SyncScriptFunction syncFunction) {
			this.syncFunction = syncFunction;
		}
		public Object call(Object requestAsObject) {
			return builder.fromJava(syncFunction.call(builder.toJava(requestAsObject), builder));
		}
	}
	
	// Must be be public to be called from javascript
	public final class AsyncInternal {
		private final AsyncScriptFunction asyncFunction;
		private AsyncInternal(AsyncScriptFunction asyncFunction) {
			this.asyncFunction = asyncFunction;
		}
		public void call(final EndManager endManager, Object requestAsObject, final Object callbackObject) {
			endManager.inc();
			
			asyncFunction.call(builder.toJava(requestAsObject), builder, new AsyncScriptFunction.Callback() {
				private boolean decCalled = false;
				/*%%% MEM LEAK!!!!
				@Override
				protected void finalize() {
					done();
				}
				*/
				@Override
				public void done() {
					doExecute(new Runnable() {
						@Override
						public void run() {
							if (decCalled) {
								return;
							}
							decCalled = true;
							endManager.dec();
						}
					});
				}
				@Override
				public AsyncScriptFunction.Callback handle(final ScriptElement response) {
					doExecute(new Runnable() {
						@Override
						public void run() {
							if (endManager.isEnded()) {
								LOGGER.warn("Callback called on a terminated object");
								return;
							}
							
							try {
								try {
									scriptEngine.put("callback", callbackObject);
									scriptEngine.put("r", builder.fromJava(response));
									scriptEngine.eval(""
										+ "(function(callback, r) {\n"
											+ "if (callback) {\n"
												+ "callback(r);\n"
											+ "}\n"
										+ "})(callback, r)");
								} finally {
									scriptEngine.put("callback", null);
									scriptEngine.put("r", null);
								}
							} catch (Throwable se) {
								LOGGER.error("Callback script error (response = {})", response, se);
								endManager.fail(new Exception(se));
							}
						}
					});
					return this;
				}
			});
		}
	}
	
	// private final Set<String> executed = new HashSet<>();
	
	private final class InnerEngine implements Engine {
		private Map<String, Object> context = null;
		private final Map<String, SyncInternal> syncFunctions = new HashMap<>();
		private final Map<String, AsyncInternal> asyncFunctions = new HashMap<>();
		
		public InnerEngine(final InnerEngine parent) {
			if (parent != null) {
				doExecute(new Runnable() {
					@Override
					public void run() {
						syncFunctions.putAll(parent.syncFunctions);
						asyncFunctions.putAll(parent.asyncFunctions);
						if (parent.context != null) {
							context = new HashMap<>(parent.context);
						}
					}
				});
			}
		}
		
		@Override
		public Engine sub() {
			return new InnerEngine(this);
		}
		
		@Override
		public void register(final String function, final SyncScriptFunction syncFunction) {
			doExecute(new Runnable() {
				@Override
				public void run() {
					syncFunctions.put(function, new SyncInternal(syncFunction));
				}
			});
		}
		@Override
		public void register(final String function, final AsyncScriptFunction asyncFunction) {
			doExecute(new Runnable() {
				@Override
				public void run() {
					asyncFunctions.put(function, new AsyncInternal(asyncFunction));
				}
			});
		}
		
		@Override
		public void eval(final String script, final Map<String, ?> parameters, final End end) {
			doExecute(new Runnable() {
				@Override
				public void run() {
					StringBuilder b = new StringBuilder();
					b.append("(function($");
					if (parameters != null) {
						for (String p : parameters.keySet()) {
							b.append(", ").append(p);
						}
					}
					for (String f : syncFunctions.keySet()) {
						b.append(", ").append(f);
					}
					for (String f : asyncFunctions.keySet()) {
						b.append(", ").append(f);
					}
					b.append(") {\n");
					b.append(script);
					b.append(";\n");
					b.append("return $;\n"
							+ "})(")
						.append(UNICITY_PREFIX).append("$ || {}");
					if (parameters != null) {
						for (String p : parameters.keySet()) {
							b.append(", ").append(UNICITY_PREFIX).append(p);
						}
					}
					for (String f : syncFunctions.keySet()) {
						b.append(", (function() {\n"
								+ "var ").append(UNICITY_PREFIX).append(UNICITY_PREFIX).append("f = ").append(UNICITY_PREFIX).append(f).append(";\n"
								+ "return function(request) {\n"
									+ "return " + UNICITY_PREFIX + "tojs(").append(UNICITY_PREFIX).append(UNICITY_PREFIX).append("f.call(" + UNICITY_PREFIX + "fromjs(request)));\n"
								+ "}\n"
							+ "})()\n");
					}
					for (String f : asyncFunctions.keySet()) {
						b.append(", (function() {\n"
								+ "var ").append(UNICITY_PREFIX).append(UNICITY_PREFIX).append("f = ").append(UNICITY_PREFIX).append(f).append(";\n"
								+ "var ").append(UNICITY_PREFIX).append(UNICITY_PREFIX).append("e = ").append(UNICITY_PREFIX).append("endManager;\n"
								+ "return function(request, callback) {\n"
									+ "").append(UNICITY_PREFIX).append(UNICITY_PREFIX).append("f.call(").append(UNICITY_PREFIX).append(UNICITY_PREFIX).append("e, " + UNICITY_PREFIX + "fromjs(request), ");
									b.append("function(r) { if (callback) { callback(" + UNICITY_PREFIX + "tojs(r)); } }");
								b.append(");\n"
								+ "}\n"
							+ "})()\n");
					}
					b.append(");");
					
					String composedScript = b.toString();
					
					EndManager endManager = new EndManager(end);
					endManager.inc();
					try {
						try {
							try {
								scriptEngine.put(UNICITY_PREFIX + "$", context);
								scriptEngine.put(UNICITY_PREFIX + "endManager", endManager);
								if (parameters != null) {
									for (Map.Entry<String, ?> e : parameters.entrySet()) {
										scriptEngine.put(UNICITY_PREFIX + e.getKey(), e.getValue());
									}
								}
								for (Map.Entry<String, SyncInternal> e : syncFunctions.entrySet()) {
									scriptEngine.put(UNICITY_PREFIX + e.getKey(), e.getValue());
								}
								for (Map.Entry<String, AsyncInternal> e : asyncFunctions.entrySet()) {
									scriptEngine.put(UNICITY_PREFIX + e.getKey(), e.getValue());
								}
								/*
								if (!executed.contains(composedScript)) {
									LOGGER.warn("Never executed: {} / {}", composedScript, executed);
									executed.add(composedScript);
								}
								*/
								context = cast(scriptEngine.eval(composedScript));
							} finally {
								scriptEngine.put(UNICITY_PREFIX + "$", null);
								scriptEngine.put(UNICITY_PREFIX + "endManager", null);
								if (parameters != null) {
									for (String p : parameters.keySet()) {
										scriptEngine.put(UNICITY_PREFIX + p, null);
									}
								}
								for (String f : syncFunctions.keySet()) {
									scriptEngine.put(UNICITY_PREFIX + f, null);
								}
								for (String f : asyncFunctions.keySet()) {
									scriptEngine.put(UNICITY_PREFIX + f, null);
								}
							}
						} catch (Throwable se) {
							LOGGER.error("Script error\n{}\n", composedScript, se);
							endManager.fail(new Exception(se));
						}
					} finally {
						endManager.dec();
					}
				}
			});
		}
	}
	
	@Override
	public Engine engine() {
		return new InnerEngine(null);
	}

	private void doExecute(Runnable r) {
		executor.execute(r);
	}
}
