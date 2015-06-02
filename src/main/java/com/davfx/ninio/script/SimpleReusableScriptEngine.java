package com.davfx.ninio.script;

import java.util.LinkedList;
import java.util.List;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

public final class SimpleReusableScriptEngine implements ReusableScriptEngine {

	public static interface ScriptEngineFactory {
		ScriptEngine engine();
	}
	
	private final ScriptEngineFactory scriptEngineFactory;
	private final List<String> prepared = new LinkedList<>();
	
	public SimpleReusableScriptEngine(ScriptEngineFactory scriptEngineFactory) {
		this.scriptEngineFactory = scriptEngineFactory;
	}
	
	@Override
	public void prepare(String script) throws ScriptException {
		prepared.add(script);
	}

	@Override
	public EngineGenerator finish() throws ScriptException {
		final ScriptEngine engine = scriptEngineFactory.engine();

		for (String s : prepared) {
			engine.eval(s);
		}

		return new EngineGenerator() {
			@Override
			public Engine get() {
				return new Engine() {
					@Override
					public void bind(String var, Object value) {
						engine.getBindings(ScriptContext.ENGINE_SCOPE).put(var, value);
					}
					@Override
					public void unbind(String var) {
						engine.getBindings(ScriptContext.ENGINE_SCOPE).remove(var);
					}
					@Override
					public void eval(String script) throws ScriptException {
						engine.eval(script);
					}
				};
			}
		};
	}
}
