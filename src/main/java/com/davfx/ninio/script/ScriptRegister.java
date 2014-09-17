package com.davfx.ninio.script;

import com.google.gson.JsonElement;

public interface ScriptRegister {
	ScriptRegister register(String functionId, AsyncScriptFunction<JsonElement> function);
	ScriptRegister register(String functionId, SyncScriptFunction<JsonElement> function);
}
