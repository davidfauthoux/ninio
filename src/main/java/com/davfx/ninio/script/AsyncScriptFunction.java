package com.davfx.ninio.script;

import com.davfx.ninio.common.Closeable;

public interface AsyncScriptFunction<T> {
	interface Callback<T> extends Closeable {
		void handle(T response);
	}
	void call(T request, Callback<T> callback);
}
