package com.davfx.ninio.script;

public interface AsyncScriptFunction<T, U> {
	interface Callback<U> { // extends Closeable {
		void handle(U response);
		void done();
	}
	void call(T request, Callback<U> callback);
}
