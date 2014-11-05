package com.davfx.ninio.script.util;

import java.io.IOException;

import com.davfx.ninio.ping.PingClientHandler;
import com.davfx.ninio.ping.PingableAddress;
import com.davfx.ninio.ping.util.PingClientCache;
import com.davfx.ninio.script.AsyncScriptFunction;
import com.davfx.ninio.script.RegisteredFunctionsScriptRunner;
import com.davfx.util.ConfigUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class PingAvailable {
	public static final String CALL_FUNCTION_NAME = ConfigUtils.load(PingAvailable.class).getString("script.functions.ping");

	private final PingClientCache client;

	public PingAvailable(PingClientCache client) {
		this.client = client;
	}
	
	public void registerOn(RegisteredFunctionsScriptRunner runner) {
		runner.register(CALL_FUNCTION_NAME, new AsyncScriptFunction<JsonElement>() {
			@Override
			public void call(JsonElement request, final AsyncScriptFunction.Callback<JsonElement> userCallback) {
				JsonObject r = request.getAsJsonObject();
				
				String host = JsonUtils.getString(r, "host", "localhost");
				PingClientCache.Connectable c = client.get(host);

				final int numberOfRetries = JsonUtils.getInt(r, "retry", 1);
				final double timeBetweenRetries = JsonUtils.getDouble(r, "between", 1d);
				final double retryTimeout = JsonUtils.getDouble(r, "timeout", 10d);
				
				final PingableAddress address;
				try {
					address = PingableAddress.from(host);
				} catch (IOException e) {
					JsonObject rr = new JsonObject();
					rr.add("error", new JsonPrimitive(e.getMessage()));
					userCallback.handle(rr);
					return;
				}
				
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
						callback.ping(address, numberOfRetries, timeBetweenRetries, retryTimeout, new PingClientHandler.Callback.PingCallback() {
							@Override
							public void failed(IOException e) {
								JsonObject r = new JsonObject();
								r.add("error", new JsonPrimitive(e.getMessage()));
								userCallback.handle(r);
							}
							
							@Override
							public void pong(int[] statuses, double[] times) {
								JsonArray a = new JsonArray();
								for (int i = 0; i < statuses.length; i++) {
									JsonObject r = new JsonObject();
									if (statuses[i] == PingClientHandler.VALID_STATUS) {
										r.add("time", new JsonPrimitive(times[i]));
									} else {
										r.add("error", new JsonPrimitive(statuses[i]));
									}
									a.add(r);
								}
								userCallback.handle(a);
							}
						});
					}
				});
			}
		});
	}
}
