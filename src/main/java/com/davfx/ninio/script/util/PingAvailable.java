package com.davfx.ninio.script.util;

import java.io.IOException;

import com.davfx.ninio.ping.PingClientHandler;
import com.davfx.ninio.ping.util.PingClientCache;
import com.davfx.ninio.script.AsyncScriptFunction;
import com.davfx.ninio.script.ScriptRunner;
import com.davfx.util.ConfigUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class PingAvailable {
	public static final String CALL_FUNCTION_NAME = ConfigUtils.load(PingAvailable.class).getString("script.functions.ping");

	private PingAvailable() {
	}
	
	public static void register(ScriptRunner runner, final PingClientCache client, final Cache cache) {
		runner.register(CALL_FUNCTION_NAME, new AsyncScriptFunction() {
			@Override
			public void call(JsonElement request, AsyncScriptFunction.Callback userCallback) {
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
