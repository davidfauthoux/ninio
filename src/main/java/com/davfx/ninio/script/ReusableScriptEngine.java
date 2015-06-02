package com.davfx.ninio.script;

import javax.script.ScriptException;

public interface ReusableScriptEngine {
	void prepare(String script) throws ScriptException;
	
	interface EngineGenerator {
		Engine get();
	}
	interface Engine {
		void bind(String var, Object value);
		void unbind(String var);
		void eval(String script) throws ScriptException;
	}
	
	EngineGenerator finish() throws ScriptException;
}
