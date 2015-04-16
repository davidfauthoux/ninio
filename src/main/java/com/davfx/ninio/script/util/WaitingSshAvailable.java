package com.davfx.ninio.script.util;

import java.io.IOException;
import java.util.regex.Pattern;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.remote.WaitingRemoteClientCache;
import com.davfx.ninio.remote.WaitingRemoteClientHandler;
import com.davfx.ninio.script.AsyncScriptFunction;
import com.davfx.ninio.ssh.SshClientConfigurator;
import com.davfx.util.ConfigUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

//TODO Check if time&cut parameters are really useful (ssh compared to telnet)
public final class WaitingSshAvailable {
	public static final String CALL_FUNCTION_NAME = ConfigUtils.load(WaitingSshAvailable.class).getString("script.functions.ssh");

	private WaitingSshAvailable() {
	}
	
	public static void link(RegisteredFunctionsScriptRunner runner, final WaitingRemoteClientCache client, final Cache cache) {
		runner.register(CALL_FUNCTION_NAME).link(CALL_FUNCTION_NAME, new AsyncScriptFunction<JsonElement>() {
			@Override
			public void call(JsonElement request, final AsyncScriptFunction.Callback<JsonElement> userCallback) {
				JsonObject r = request.getAsJsonObject();
				
				Address address = new Address(JsonUtils.getString(r, "host", "localhost"), JsonUtils.getInt(r, "port", SshClientConfigurator.DEFAULT_PORT));

				final AsyncScriptFunctionCallbackManager m = new AsyncScriptFunctionCallbackManager(userCallback);

				final String command = JsonUtils.getString(r, "command");
				final double time = JsonUtils.getDouble(r, "time", Double.NaN);
				final Pattern cut = JsonUtils.getPattern(r, "cut", null);
				JsonElement init = r.get("init");

				final Cache.ForAddressCache internalCache;
				if (cache == null) {
					internalCache = null;
				} else {
					internalCache = cache.get(address);
					if (!internalCache.register(command, m)) { // Check already waiting, register if so, set waiting if not
						return;
					}
				}
				
				WaitingRemoteClientCache.Connectable c = client.get(address);

				if (init != null) {
					for (JsonElement e : init.getAsJsonArray()) {
						JsonObject o = e.getAsJsonObject();
						c.init(JsonUtils.getString(o, "command"), JsonUtils.getDouble(o, "time", Double.NaN), JsonUtils.getPattern(o, "cut", null));
					}
				}

				c.connect(new WaitingRemoteClientHandler() {
					@Override
					public void failed(IOException e) {
						m.failed(e);
						if (internalCache != null) {
							internalCache.failed(command, e);
						}
					}
					@Override
					public void close() {
						m.close();
						if (internalCache != null) {
							internalCache.close(command);
						}
					}
					@Override
					public void launched(final String init, final Callback callback) {
						callback.send(command, time, cut, new WaitingRemoteClientHandler.Callback.SendCallback() {
							@Override
							public void failed(IOException e) {
								m.failed(e);
								if (internalCache != null) {
									internalCache.failed(command, e);
								}
								callback.close();
							}
							@Override
							public void received(String text) {
								JsonObject t = new JsonObject();
								t.add("init", new JsonPrimitive(init));
								t.add("response", new JsonPrimitive(text));
								m.done(t);
								if (internalCache != null) {
									internalCache.done(command, t);
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
