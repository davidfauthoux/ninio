package com.davfx.ninio.script;

import com.google.gson.JsonElement;

public interface ScriptRegister {
	ScriptRegister register(String functionId);
	ScriptRegister link(String functionId, AsyncScriptFunction<JsonElement> function);
	ScriptRegister link(String functionId, SyncScriptFunction<JsonElement> function);
}
