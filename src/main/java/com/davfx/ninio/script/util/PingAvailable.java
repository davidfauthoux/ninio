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
	
	public static void link(RegisteredFunctionsScript.Runner runner, final PingClientCache client, final Cache cache) {
		runner.link(CALL_FUNCTION_NAME, new AsyncScriptFunction<JsonElement>() {
			@Override
			public void call(JsonElement request, final AsyncScriptFunction.Callback<JsonElement> userCallback) {
				JsonObject r = request.getAsJsonObject();
				
				final String host = JsonUtils.getString(r, "host", "localhost");

				final AsyncScriptFunctionCallbackManager m = new AsyncScriptFunctionCallbackManager(userCallback);

				final Cache.ForAddressCache internalCache;
				if (cache == null) {
					internalCache = null;
				} else {
					internalCache = cache.get(null);
					if (!internalCache.register(host, m)) { // Check already waiting, register if so, set waiting if not
						return;
					}
				}

				PingClientCache.Connectable c = client.get(host);

				c.connect(new PingClientHandler() {
					@Override
					public void failed(IOException e) {
						m.failed(e);
						if (internalCache != null) {
							internalCache.failed(host, e);
						}
					}
					@Override
					public void close() {
						m.close();
						if (internalCache != null) {
							internalCache.close(host);
						}
					}
					@Override
					public void launched(final Callback callback) {
						callback.ping(host, new PingClientHandler.Callback.PingCallback() {
							@Override
							public void failed(IOException e) {
								m.failed(e);
								if (internalCache != null) {
									internalCache.failed(host, e);
								}
								callback.close();
							}
							@Override
							public void pong(double time) {
								JsonPrimitive r = new JsonPrimitive(time);
								m.done(r);
								if (internalCache != null) {
									internalCache.done(host, r);
								}
								callback.close();
							}
						});
					}
				});
			}
		});
	}
}
