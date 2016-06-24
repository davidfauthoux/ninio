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
import com.davfx.ninio.util.SerialExecutor;

//import lu.flier.script.V8ScriptEngineFactory;

public final class ExecutorScriptRunner implements ScriptRunner {
	private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorScriptRunner.class);

	private ScriptEngine scriptEngine;
	private final Executor executor = new SerialExecutor(ExecutorScriptRunner.class);
	private boolean wrapConvert;

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
				
				if (scriptEngine.getFactory().getEngineName().equals("Mozilla Rhino")) {
					try {
						scriptEngine.eval("function __js(o) {"
							+ "if (o instanceof java.util.Map) {"
								+ "var r = {};"
								+ "var i = o.keySet().iterator();"
								+ "while (i.hasNext()) {"
									+ "var k = i.next();"
									+ "r[k] = __js(o.get(k));"
								+ "}"
								+ "return r;"
								+ "} else if (o instanceof java.lang.Number) {"
									+ "return 1.0 * o;"
								+ "} else {"
									+ "return '' + o;"
								+ "}"
							+ "}");
					} catch (ScriptException e) {
						LOGGER.error("Could not prepare engine", e);
					}
					wrapConvert = true;
				} else {
					wrapConvert = false;
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
	private static <T, U> SyncScriptFunction<Object, Object> cast(SyncScriptFunction<T, U> f) {
		return (SyncScriptFunction<Object, Object>) f;
	}
	@SuppressWarnings("unchecked")
	private static <T, U> AsyncScriptFunction<Object, Object> cast(AsyncScriptFunction<T, U> f) {
		return (AsyncScriptFunction<Object, Object>) f;
	}
	@SuppressWarnings("unchecked")
	private static Map<String, Object> cast(Object f) {
		return (Map<String, Object>) f;
	}
	
	// Must be be public to be called from javascript
	public final class SyncInternal {
		private final SyncScriptFunction<Object, Object> syncFunction;
		private SyncInternal(SyncScriptFunction<Object, Object> syncFunction) {
			this.syncFunction = syncFunction;
		}
		public Object call(Object requestAsObject) {
			return syncFunction.call(requestAsObject);
		}
	}
	
	// Must be be public to be called from javascript
	public final class AsyncInternal {
		private final AsyncScriptFunction<Object, Object> asyncFunction;
		private AsyncInternal(AsyncScriptFunction<Object, Object> asyncFunction) {
			this.asyncFunction = asyncFunction;
		}
		public void call(final EndManager endManager, Object requestAsObject, final Object callbackObject) {
			endManager.inc();
			
			asyncFunction.call(requestAsObject, new AsyncScriptFunction.Callback<Object>() {
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
				public AsyncScriptFunction.Callback<Object> handle(final Object response) {
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
									scriptEngine.put("r", response);
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
								LOGGER.error("Callback script error", se);
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
		public <T, U> void register(final String function, final SyncScriptFunction<T, U> syncFunction) {
			doExecute(new Runnable() {
				@Override
				public void run() {
					syncFunctions.put(function, new SyncInternal(cast(syncFunction)));
				}
			});
		}
		@Override
		public <T, U> void register(final String function, final AsyncScriptFunction<T, U> asyncFunction) {
			doExecute(new Runnable() {
				@Override
				public void run() {
					asyncFunctions.put(function, new AsyncInternal(cast(asyncFunction)));
				}
			});
		}
		
		@Override
		public <P> void eval(final String script, final Map<String, ?> parameters, final End end) {
			doExecute(new Runnable() {
				@Override
				public void run() {
					StringBuilder prefixBuilder = new StringBuilder();
					int max = 0;
					for (String f : syncFunctions.keySet()) {
						max = Math.max(max, f.length());
					}
					for (int i = 0; i < max; i++) {
						prefixBuilder.append('_');
					}
					String prefix = prefixBuilder.toString();
					
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
						.append(prefix).append("$ || {}");
					if (parameters != null) {
						for (String p : parameters.keySet()) {
							b.append(", ").append(prefix).append(p);
						}
					}
					for (String f : syncFunctions.keySet()) {
						b.append(", (function() {\n"
								+ "var ").append(prefix).append(prefix).append("f = ").append(prefix).append(f).append(";\n"
								+ "return function(request) {\n"
									+ "return ");
						if (wrapConvert) {
							b.append("__js(");
						}
						b.append(prefix).append(prefix).append("f.call(request)");
						if (wrapConvert) {
							b.append(")");
						}
						b.append(";\n"
								+ "}\n"
							+ "})()\n");
					}
					for (String f : asyncFunctions.keySet()) {
						b.append(", (function() {\n"
								+ "var ").append(prefix).append(prefix).append("f = ").append(prefix).append(f).append(";\n"
								+ "var ").append(prefix).append(prefix).append("e = ").append(prefix).append("endManager;\n"
								+ "return function(request, callback) {\n"
									+ "").append(prefix).append(prefix).append("f.call(").append(prefix).append(prefix).append("e, request, ");
						if (wrapConvert) {
							b.append("function(r) { if (callback) { callback(__js(r)); } }");
						} else {
							b.append("callback || function() {}");
						}
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
								scriptEngine.put(prefix + "$", context);
								scriptEngine.put(prefix + "endManager", endManager);
								if (parameters != null) {
									for (Map.Entry<String, ?> e : parameters.entrySet()) {
										scriptEngine.put(prefix + e.getKey(), e.getValue());
									}
								}
								for (Map.Entry<String, SyncInternal> e : syncFunctions.entrySet()) {
									scriptEngine.put(prefix + e.getKey(), e.getValue());
								}
								for (Map.Entry<String, AsyncInternal> e : asyncFunctions.entrySet()) {
									scriptEngine.put(prefix + e.getKey(), e.getValue());
								}
								/*
								if (!executed.contains(composedScript)) {
									LOGGER.warn("Never executed: {} / {}", composedScript, executed);
									executed.add(composedScript);
								}
								*/
								context = cast(scriptEngine.eval(composedScript));
							} finally {
								scriptEngine.put(prefix + "$", null);
								scriptEngine.put(prefix + "endManager", null);
								if (parameters != null) {
									for (String p : parameters.keySet()) {
										scriptEngine.put(prefix + p, null);
									}
								}
								for (String f : syncFunctions.keySet()) {
									scriptEngine.put(prefix + f, null);
								}
								for (String f : asyncFunctions.keySet()) {
									scriptEngine.put(prefix + f, null);
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
