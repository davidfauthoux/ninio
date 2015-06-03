package com.davfx.ninio.script;

import com.google.gson.JsonElement;

public interface SyncScriptFunction {
	JsonElement call(JsonElement request);
}
