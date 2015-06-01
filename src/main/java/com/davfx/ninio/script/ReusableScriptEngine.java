package com.davfx.ninio.script;

import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

public final class ReusableScriptEngine {

	private final ScriptEngine engine;
	
	public ReusableScriptEngine(ScriptEngine engine) {
		this.engine = engine;
	}
	
	public void prepare(String script) throws ScriptException {
		engine.eval(script);
	}
	
	public static interface EngineGenerator {
		Engine get();
	}
	public static interface Engine {
		void bind(String var, Object value);
		void unbind(String var);
		void eval(String script) throws ScriptException;
	}
	
	public EngineGenerator finish() {
		final Map<String, Object> preparedBindings = new HashMap<>();
		Bindings b = engine.getBindings(ScriptContext.ENGINE_SCOPE);
		for (Map.Entry<String, Object> e : b.entrySet()) {
			preparedBindings.put(e.getKey(), e.getValue());
		}

		// Clear by security purpose
		engine.setBindings(new SimpleBindings(), ScriptContext.ENGINE_SCOPE);

		return new EngineGenerator() {
			@Override
			public Engine get() {
				return new Engine() {
					private final Map<String, Object> bindings = new HashMap<>();
					
					private void save() {
						bindings.clear();
						Bindings b = engine.getBindings(ScriptContext.ENGINE_SCOPE);
						for (Map.Entry<String, Object> e : b.entrySet()) {
							bindings.put(e.getKey(), e.getValue());
						}
						
						// Clear to release memory
						engine.setBindings(new SimpleBindings(), ScriptContext.ENGINE_SCOPE);
					}
					
					private void restore() {
						Bindings b = new SimpleBindings();
						for (Map.Entry<String, Object> e : bindings.entrySet()) {
							b.put(e.getKey(), e.getValue());
						}
						engine.setBindings(b, ScriptContext.ENGINE_SCOPE);
					}
					
					{
						for (Map.Entry<String, Object> e : preparedBindings.entrySet()) {
							bindings.put(e.getKey(), e.getValue());
						}
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
