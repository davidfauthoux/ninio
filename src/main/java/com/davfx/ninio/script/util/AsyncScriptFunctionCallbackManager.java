package com.davfx.ninio.script.util;

import java.io.IOException;

import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Failable;
import com.davfx.ninio.script.AsyncScriptFunction;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

final class AsyncScriptFunctionCallbackManager implements Closeable, Failable {
	private final AsyncScriptFunction.Callback<JsonElement> callback;
	private boolean closed = false;

	public AsyncScriptFunctionCallbackManager(AsyncScriptFunction.Callback<JsonElement> wrappee) {
		this.callback = wrappee;
	}
	
	@Override
	public void failed(IOException e) {
		if (closed) {
			return;
		}
		closed = true;

		JsonObject r = new JsonObject();
		r.add("error", new JsonPrimitive(e.getMessage()));
		callback.handle(r);
		
		// callback.close();
	}
	
	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;

		JsonObject r = new JsonObject();
		r.add("error", new JsonPrimitive("Prematurely closed"));
		callback.handle(r);
		
		// callback.close();
	}
	
	/*
	public void partially(JsonElement result) {
		if (closed) {
			return;
		}

		JsonObject r = new JsonObject();
		r.add("result", result);
		callback.handle(r);
	}
	*/

	public void done(JsonElement result) {
		if (closed) {
			return;
		}
		closed = true;

		JsonObject r = new JsonObject();
		r.add("result", result);
		callback.handle(r);
		
		// callback.close();
	}
}
