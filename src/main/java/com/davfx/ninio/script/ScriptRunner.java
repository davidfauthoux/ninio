package com.davfx.ninio.script;

import com.davfx.ninio.common.Failable;


public interface ScriptRunner<T> {
	void eval(Iterable<String> script, Failable fail, AsyncScriptFunction<T> asyncFunction, SyncScriptFunction<T> syncFunction);
}
