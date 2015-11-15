package com.davfx.ninio.script;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Failable;
import com.davfx.script.AsyncScriptFunction;
import com.google.gson.JsonElement;

final class AsyncScriptFunctionCallbackManager implements Failable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AsyncScriptFunctionCallbackManager.class);
	
	private final AsyncScriptFunction.Callback callback;
	private boolean closed = false;

	public AsyncScriptFunctionCallbackManager(AsyncScriptFunction.Callback wrappee) {
		this.callback = wrappee;
	}
	
	@Override
	public void failed(IOException e) {
		if (closed) {
			return;
		}
		closed = true;

		LOGGER.debug("Error returned: {}", e.getMessage());
		callback.handle(null);
	}
	
	public void done(JsonElement result) {
		if (closed) {
			return;
		}
		closed = true;

		callback.handle(result);
	}
}
