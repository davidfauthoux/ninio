package com.davfx.ninio.script;

import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

// Beware! The objects created with prepared scripts are shared between the eval'd scripts BUT the directly referenced ones
// Example:
// - prepared: var s = 'string';
// - prepared: var o = { a:'aa', b:'bb' };
// - eval: println(s) -> 'string'
// - eval: println(o) -> { a:'aa', b:'bb' }
// - eval: s = '--'; println(s) -> '--'
// - eval: println(s) -> 'string' // BACK TO prepared s (MODIFIED BY PREVIOUS eval SCRIPT BUT DIRECT REFERENCE)
// - eval: o.a = '--'; println(o) -> { a:'--', b:'bb' }
// - eval: println(o) -> { a:'--', b:'bb' } // BACK TO prepared o, BUT MODIFIED BY PREVIOUS eval SCRIPT

public final class SharingReusableScriptEngine implements ReusableScriptEngine {

	private final ScriptEngine engine;
	
	public SharingReusableScriptEngine(ScriptEngine engine) {
		this.engine = engine;
	}
	
	@Override
	public void prepare(String script) throws ScriptException {
		engine.eval(script);
	}
	
	private static void clearBindings(ScriptEngine engine) throws ScriptException {
		// Java7: // engine.setBindings(new SimpleBindings(), ScriptContext.ENGINE_SCOPE);
		
		// Java7/Java8:
		for (String k : engine.getBindings(ScriptContext.ENGINE_SCOPE).keySet()) {
			engine.eval(k + "=undefined;");
		}
		// engine.getBindings(ScriptContext.ENGINE_SCOPE).clear();

		// This is not working in Java8, probably because it keeps track of closure somewhere else than in bindings // engine.setBindings(new SimpleBindings(), ScriptContext.ENGINE_SCOPE);
	}
	
	@Override
	public EngineGenerator finish() throws ScriptException {
		final Map<String, Object> preparedBindings = new HashMap<>();
		Bindings b = engine.getBindings(ScriptContext.ENGINE_SCOPE);
		for (Map.Entry<String, Object> e : b.entrySet()) {
			preparedBindings.put(e.getKey(), e.getValue());
		}

		// Clear by security purpose
		clearBindings(engine);

		return new EngineGenerator() {
			@Override
			public Engine get() {
				return new Engine() {
					private final Map<String, Object> bindings = new HashMap<>();
					
					private void save() throws ScriptException {
						bindings.clear();
						Bindings b = engine.getBindings(ScriptContext.ENGINE_SCOPE);
						for (Map.Entry<String, Object> e : b.entrySet()) {
							bindings.put(e.getKey(), e.getValue());
						}
						
						// Clear to release memory
						clearBindings(engine);
					}
					
					private void restore() throws ScriptException {
						clearBindings(engine);
						Bindings b = engine.getBindings(ScriptContext.ENGINE_SCOPE);
						for (Map.Entry<String, Object> e : bindings.entrySet()) {
							b.put(e.getKey(), e.getValue());
						}
					}
					
					{
						bindings.putAll(preparedBindings);
					}
					
					@Override
					public void bind(String var, Object value) {
						bindings.put(var, value);
					}
					@Override
					public void unbind(String var) {
						bindings.remove(var);
					}
					
					@Override
					public void eval(String script) throws ScriptException {
						restore();
						engine.eval(script);
						save();
					}
				};
			}
		};
	}
}
