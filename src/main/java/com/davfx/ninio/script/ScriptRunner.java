package com.davfx.ninio.script;

import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Failable;


public interface ScriptRunner<T> extends AutoCloseable, Closeable {
	void prepare(Iterable<String> script, Failable fail);
	void eval(Iterable<String> script, Failable fail, AsyncScriptFunction<T> asyncFunction, SyncScriptFunction<T> syncFunction);
}
