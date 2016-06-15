package com.davfx.ninio.script;

public interface SyncScriptFunction<T, U> {
	U call(T request);
}
