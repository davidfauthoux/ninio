package com.davfx.ninio.script;

public interface AsyncScriptFunction {
	interface Callback {
		Callback handle(ScriptElement response);
		void done();
	}
	void call(ScriptElement request, ScriptElementBuilder builder, Callback callback);
}
