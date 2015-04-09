package com.davfx.ninio.script.util;

import java.io.IOException;

import com.davfx.ninio.ping.OldPingClientHandler;
import com.davfx.ninio.ping.PingableAddress;
import com.davfx.ninio.ping.util.OldPingClientCache;
import com.davfx.ninio.script.AsyncScriptFunction;
import com.davfx.util.ConfigUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.typesafe.config.Config;

@Deprecated
public final class OldPingAvailable {
	private static final Config CONFIG = ConfigUtils.load(OldPingAvailable.class);
	public static final String CALL_FUNCTION_NAME = CONFIG.getString("script.functions.ping");
	private static final int DEFAULT_NUMBER_OF_RETRIES = CONFIG.getInt("script.ping.default.numberOfRetries");
	private static final double DEFAULT_TIME_BETWEEN_RETRIES = CONFIG.getInt("script.ping.default.timeBetweenRetries");
	private static final double RETRY_TIMEOUT = CONFIG.getInt("script.ping.default.retryTimeout");

	private OldPingAvailable() {
	}
	
	public static void link(RegisteredFunctionsScriptRunner runner, final OldPingClientCache client) {
		runner.register(CALL_FUNCTION_NAME).link(CALL_FUNCTION_NAME, new AsyncScriptFunction<JsonElement>() {
			@Override
			public void call(JsonElement request, final AsyncScriptFunction.Callback<JsonElement> userCallback) {
				JsonObject r = request.getAsJsonObject();
				
				String host = JsonUtils.getString(r, "host", "localhost");
				OldPingClientCache.Connectable c = client.get(host);

				final int numberOfRetries = JsonUtils.getInt(r, "retry", DEFAULT_NUMBER_OF_RETRIES);
				final double timeBetweenRetries = JsonUtils.getDouble(r, "between", DEFAULT_TIME_BETWEEN_RETRIES);
				final double retryTimeout = JsonUtils.getDouble(r, "timeout", RETRY_TIMEOUT);

				final AsyncScriptFunctionCallbackManager m = new AsyncScriptFunctionCallbackManager(userCallback);

				final PingableAddress address;
				try {
					address = PingableAddress.from(host);
				} catch (IOException e) {
					m.failed(e);
					return;
				}
				
				c.connect(new OldPingClientHandler() {
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
						callback.ping(address, numberOfRetries, timeBetweenRetries, retryTimeout, new OldPingClientHandler.Callback.PingCallback() {
							@Override
							public void failed(IOException e) {
								m.failed(e);
								callback.close();
							}
							
							@Override
							public void pong(int[] statuses, double[] times) {
								JsonArray a = new JsonArray();
								for (int i = 0; i < statuses.length; i++) {
									JsonObject r = new JsonObject();
									if (statuses[i] == OldPingClientHandler.VALID_STATUS) {
										r.add("time", new JsonPrimitive(times[i]));
									} else {
										r.add("error", new JsonPrimitive(statuses[i]));
									}
									a.add(r);
								}
								m.done(a);
								callback.close();
							}
						});
					}
				});
			}
		});
	}
}
