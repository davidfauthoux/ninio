package com.davfx.ninio.script.util;

import java.io.IOException;

import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Failable;
import com.davfx.ninio.script.AsyncScriptFunction;
import com.davfx.ninio.util.CheckAllocationObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

final class AsyncScriptFunctionCallbackManager extends CheckAllocationObject implements Closeable, Failable {
	private final AsyncScriptFunction.Callback callback;
	private boolean closed = false;

	public AsyncScriptFunctionCallbackManager(AsyncScriptFunction.Callback wrappee) {
		super(AsyncScriptFunctionCallbackManager.class);
		this.callback = wrappee;
	}
	
	@Override
	public synchronized void failed(IOException e) {
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
	public synchronized void close() {
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
	public synchronized void partially(JsonElement result) {
		if (closed) {
			return;
		}

		JsonObject r = new JsonObject();
		r.add("result", result);
		callback.handle(r);
	}
	*/

	public synchronized void done(JsonElement result) {
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
