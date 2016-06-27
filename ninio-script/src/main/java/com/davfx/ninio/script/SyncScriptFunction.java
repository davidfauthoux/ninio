package com.davfx.ninio.script;

public interface SyncScriptFunction {
	ScriptElement call(ScriptElement request, ScriptElementBuilder builder);
}
