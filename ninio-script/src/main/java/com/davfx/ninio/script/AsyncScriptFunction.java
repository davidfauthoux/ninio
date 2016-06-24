package com.davfx.ninio.script;

public interface AsyncScriptFunction<T, U> {
	interface Callback<U> {
		Callback<U> handle(U response);
		void done();
	}
	void call(T request, Callback<U> callback);
}
