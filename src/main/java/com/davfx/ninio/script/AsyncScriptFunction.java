package com.davfx.ninio.script;


public interface AsyncScriptFunction<T> {
	interface Callback<T> { // extends Closeable {
		void handle(T response);
	}
	void call(T request, Callback<T> callback);
}
