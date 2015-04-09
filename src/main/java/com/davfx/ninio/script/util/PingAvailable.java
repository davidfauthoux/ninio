package com.davfx.ninio.script.util;

import java.io.IOException;

import com.davfx.ninio.ping.PingClientHandler;
import com.davfx.ninio.ping.util.PingClientCache;
import com.davfx.ninio.script.AsyncScriptFunction;
import com.davfx.util.ConfigUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.typesafe.config.Config;

public final class PingAvailable {
	private static final Config CONFIG = ConfigUtils.load(PingAvailable.class);
	public static final String CALL_FUNCTION_NAME = CONFIG.getString("script.functions.ping");

	private PingAvailable() {
	}
	
	public static void link(RegisteredFunctionsScriptRunner runner, final PingClientCache client) {
		runner.register(CALL_FUNCTION_NAME).link(CALL_FUNCTION_NAME, new AsyncScriptFunction<JsonElement>() {
			@Override
			public void call(JsonElement request, final AsyncScriptFunction.Callback<JsonElement> userCallback) {
				JsonObject r = request.getAsJsonObject();
				
				final String host = JsonUtils.getString(r, "host", "localhost");
				PingClientCache.Connectable c = client.get(host);

				final AsyncScriptFunctionCallbackManager m = new AsyncScriptFunctionCallbackManager(userCallback);
				c.connect(new PingClientHandler() {
					@Override
					public void failed(IOException e) {
						m.failed(e);
					}
					@Override
					public void close() {
						m.close();
					}
					@Override
					public void launched(final Callback callback) {
						callback.ping(host, new PingClientHandler.Callback.PingCallback() {
							@Override
							public void failed(IOException e) {
								m.failed(e);
								callback.close();
							}
							@Override
							public void pong(double time) {
								m.done(new JsonPrimitive(time));
								callback.close();
							}
						});
					}
				});
			}
		});
	}
}
