package com.davfx.ninio.script.util;

import com.davfx.ninio.common.Failable;
import com.davfx.ninio.script.AsyncScriptFunction;
import com.davfx.ninio.script.SyncScriptFunction;
import com.google.gson.JsonElement;

public interface AllAvailableRunner {
	void register(String functionId);
	void prepare(Iterable<String> script, Failable fail);

	void link(Runnable onEnd);
	void link(String functionId, AsyncScriptFunction<JsonElement> function);
	void link(String functionId, SyncScriptFunction<JsonElement> function);
	void eval(Iterable<String> script, Failable fail);
}
