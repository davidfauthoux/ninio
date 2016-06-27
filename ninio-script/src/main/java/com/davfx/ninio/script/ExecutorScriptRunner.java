package com.davfx.ninio.script;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.script.com.sun.script.javascript.RhinoScriptEngineFactory;
import com.davfx.ninio.util.SerialExecutor;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

//import lu.flier.script.V8ScriptEngineFactory;

public final class ExecutorScriptRunner implements ScriptRunner {
	private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorScriptRunner.class);

	private ScriptEngine scriptEngine;
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
				
				try {
					scriptEngine.eval(""
							+ "function __tojs(o) {"
								+ "return JSON.parse(o);"
							+ "}"
							+ "function __fromjs(o) {"
								+ "return JSON.stringify(o);"
							+ "}"
							+ "");
				} catch (ScriptException e) {
					LOGGER.error("Could not prepare engine", e);
				}
				/*
				if (scriptEngine.getFactory().getEngineName().equals("Mozilla Rhino")) {
					try {
						scriptEngine.eval(""
								+ "function __tojs(o) {"
									+ "return JSON.parse(o);"
								+ "}"
								+ "function __fromjs(o) {"
									+ "return JSON.stringify(o);"
								+ "}"
								+ "");
					} catch (ScriptException e) {
						LOGGER.error("Could not prepare engine", e);
					}
				} else {
					try {
						scriptEngine.eval("function __tojs(o) {"
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
							+ "}"
							+ "function __fromjs(o) {"
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
				}*/

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
			return fromJava(syncFunction.call(toJava(requestAsObject), ExecutorScriptRunner.this));
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
			
			asyncFunction.call(toJava(requestAsObject), ExecutorScriptRunner.this, new AsyncScriptFunction.Callback() {
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
									scriptEngine.put("r", fromJava(response));
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
									+ "return __tojs(").append(prefix).append(prefix).append("f.call(__fromjs(request)));\n"
								+ "}\n"
							+ "})()\n");
					}
					for (String f : asyncFunctions.keySet()) {
						b.append(", (function() {\n"
								+ "var ").append(prefix).append(prefix).append("f = ").append(prefix).append(f).append(";\n"
								+ "var ").append(prefix).append(prefix).append("e = ").append(prefix).append("endManager;\n"
								+ "return function(request, callback) {\n"
									+ "").append(prefix).append(prefix).append("f.call(").append(prefix).append(prefix).append("e, __fromjs(request), ");
									b.append("function(r) { if (callback) { callback(__tojs(r)); } }");
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

	private static interface Internal {
		JsonElement toJs();
	}
	
	private static final class InternalScriptString implements ScriptString, Internal {
		private final JsonPrimitive value;
		public InternalScriptString(JsonPrimitive value) {
			this.value = value;
		}
		
		@Override
		public ScriptString asString() {
			return this;
		}
		
		@Override
		public ScriptObject asObject() {
			return null;
		}
		
		@Override
		public ScriptNumber asNumber() {
			return new InternalScriptNumber(value);
		}
		
		@Override
		public ScriptArray asArray() {
			return null;
		}
		
		@Override
		public String value() {
			return value.getAsString();
		}
		
		@Override
		public JsonElement toJs() {
			return value;
		}
		
		@Override
		public String toString() {
			return toJs().toString();
		}
	}
	private static final class InternalScriptNumber implements ScriptNumber, Internal {
		private final JsonPrimitive value;
		public InternalScriptNumber(JsonPrimitive value) {
			this.value = value;
		}
		
		@Override
		public ScriptString asString() {
			return new InternalScriptString(value);
		}
		
		@Override
		public ScriptObject asObject() {
			return null;
		}
		
		@Override
		public ScriptNumber asNumber() {
			return this;
		}
		
		@Override
		public ScriptArray asArray() {
			return null;
		}
		
		@Override
		public double value() {
			return value.getAsDouble();
		}
		
		@Override
		public JsonElement toJs() {
			return value;
		}
		
		@Override
		public String toString() {
			return toJs().toString();
		}
	}
	private static final class InternalScriptObject implements ScriptObject, Internal {
		private final JsonObject value;
		public InternalScriptObject(JsonObject value) {
			this.value = value;
		}
		
		@Override
		public ScriptString asString() {
			return null;
		}
		
		@Override
		public ScriptObject asObject() {
			return this;
		}
		
		@Override
		public ScriptNumber asNumber() {
			return null;
		}
		
		@Override
		public ScriptArray asArray() {
			return null;
		}
		
		@Override
		public ScriptElement get(String key) {
			return new InternalScriptElement(value.get(key));
		}
		@Override
		public Iterable<Entry> entries() {
			return Iterables.transform(value.entrySet(), new Function<java.util.Map.Entry<String, JsonElement>, ScriptObject.Entry>() {
				@Override
				public Entry apply(final java.util.Map.Entry<String, JsonElement> input) {
					return new Entry() {
						@Override
						public ScriptElement value() {
							return new InternalScriptElement(input.getValue());
						}
						@Override
						public String key() {
							return input.getKey();
						}
					};
				}
			});
		}
		
		@Override
		public JsonElement toJs() {
			return value;
		}
		
		@Override
		public String toString() {
			return toJs().toString();
		}
	}
	private static final class InternalScriptArray implements ScriptArray, Internal {
		private final JsonArray value;
		public InternalScriptArray(JsonArray value) {
			this.value = value;
		}
		
		@Override
		public ScriptString asString() {
			return null;
		}
		
		@Override
		public ScriptObject asObject() {
			return null;
		}
		
		@Override
		public ScriptNumber asNumber() {
			return null;
		}
		
		@Override
		public ScriptArray asArray() {
			return this;
		}
		
		@Override
		public Iterator<ScriptElement> iterator() {
			return Iterators.transform(value.iterator(), new Function<JsonElement, ScriptElement>() {
				@Override
				public ScriptElement apply(JsonElement input) {
					return new InternalScriptElement(input);
				}
			});
		}
		
		@Override
		public JsonElement toJs() {
			return value;
		}
		
		@Override
		public String toString() {
			return toJs().toString();
		}
	}
	private static final class InternalScriptElement implements ScriptElement, Internal {
		private final JsonElement value;
		public InternalScriptElement(JsonElement value) {
			this.value = value;
		}
		
		@Override
		public ScriptString asString() {
			return new InternalScriptString(value.getAsJsonPrimitive());
		}
		@Override
		public ScriptObject asObject() {
			return new InternalScriptObject(value.getAsJsonObject());
		}
		@Override
		public ScriptNumber asNumber() {
			return new InternalScriptNumber(value.getAsJsonPrimitive());
		}
		@Override
		public ScriptArray asArray() {
			return new InternalScriptArray(value.getAsJsonArray());
		}
		
		@Override
		public JsonElement toJs() {
			return value;
		}
		
		@Override
		public String toString() {
			return toJs().toString();
		}
	}

	private static ScriptElement toJava(Object o) {
		return new InternalScriptElement(new JsonParser().parse((String) o));
	}
	private static Object fromJava(ScriptElement o) {
		return ((Internal) o).toJs().toString();
	}
	
	@Override
	public ScriptElement string(String value) {
		return new InternalScriptString(new JsonPrimitive(value));
	}
	@Override
	public ScriptElement number(double value) {
		return new InternalScriptString(new JsonPrimitive(value));
	}
	@Override
	public ScriptObjectBuilder object() {
		return new ScriptObjectBuilder() {
			private final JsonObject o = new JsonObject();
			@Override
			public ScriptObjectBuilder put(String key, ScriptElement value) {
				o.add(key, ((Internal) value).toJs());
				return this;
			}
			
			@Override
			public ScriptObject build() {
				return new InternalScriptObject(o);
			}
		};
	}
	@Override
	public ScriptArrayBuilder array() {
		return new ScriptArrayBuilder() {
			private final JsonArray o = new JsonArray();
			@Override
			public ScriptArrayBuilder add(ScriptElement value) {
				o.add(((Internal) value).toJs());
				return this;
			}
			
			@Override
			public ScriptArray build() {
				return new InternalScriptArray(o);
			}
		};
	}
}
