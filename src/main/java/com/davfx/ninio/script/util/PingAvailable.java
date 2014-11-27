package com.davfx.ninio.script.util;

import java.io.IOException;

import com.davfx.ninio.ping.PingClientHandler;
import com.davfx.ninio.ping.util.PingClientCache;
import com.davfx.ninio.script.AsyncScriptFunction;
import com.davfx.ninio.script.RegisteredFunctionsScriptRunner;
import com.davfx.util.ConfigUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.typesafe.config.Config;

public final class PingAvailable {
	private static final Config CONFIG = ConfigUtils.load(PingAvailable.class);
	public static final String CALL_FUNCTION_NAME = CONFIG.getString("script.functions.ping");

	private final PingClientCache client;

	public PingAvailable(PingClientCache client) {
		this.client = client;
	}
	
	public void registerOn(RegisteredFunctionsScriptRunner runner) {
		runner.register(CALL_FUNCTION_NAME, new AsyncScriptFunction<JsonElement>() {
			@Override
			public void call(JsonElement request, final AsyncScriptFunction.Callback<JsonElement> userCallback) {
				JsonObject r = request.getAsJsonObject();
				
				final String host = JsonUtils.getString(r, "host", "localhost");
				PingClientCache.Connectable c = client.get(host);

				c.connect(new PingClientHandler() {
					@Override
					public void failed(IOException e) {
						JsonObject r = new JsonObject();
						r.add("error", new JsonPrimitive(e.getMessage()));
						userCallback.handle(r);
					}
					@Override
					public void close() {
					}
					@Override
					public void launched(Callback callback) {
						callback.ping(host, new PingClientHandler.Callback.PingCallback() {
							@Override
							public void failed(IOException e) {
								JsonObject r = new JsonObject();
								r.add("error", new JsonPrimitive(e.getMessage()));
								userCallback.handle(r);
							}
							@Override
							public void pong(double time) {
								JsonObject r = new JsonObject();
								r.add("result", new JsonPrimitive(time));
								userCallback.handle(r);
							}
						});
					}
				});
			}
		});
	}
}
