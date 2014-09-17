package com.davfx.ninio.script;

public interface SyncScriptFunction<T> {
	T call(T request);
}
