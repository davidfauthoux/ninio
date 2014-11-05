package com.davfx.ninio.script.util;

import java.io.IOException;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.script.AsyncScriptFunction;
import com.davfx.ninio.script.RegisteredFunctionsScriptRunner;
import com.davfx.ninio.ssh.SshClient;
import com.davfx.ninio.telnet.util.WaitingTelnetClientCache;
import com.davfx.ninio.telnet.util.WaitingTelnetClientHandler;
import com.davfx.util.ConfigUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class WaitingSshAvailable {
	public static final String CALL_FUNCTION_NAME = ConfigUtils.load(WaitingSshAvailable.class).getString("script.functions.ssh");

	private final WaitingTelnetClientCache client;

	public WaitingSshAvailable(WaitingTelnetClientCache client) {
		this.client = client;
	}
	
	public void registerOn(RegisteredFunctionsScriptRunner runner) {
		runner.register(CALL_FUNCTION_NAME, new AsyncScriptFunction<JsonElement>() {
			@Override
			public void call(JsonElement request, final AsyncScriptFunction.Callback<JsonElement> userCallback) {
				JsonObject r = request.getAsJsonObject();
				
				Address address = new Address(JsonUtils.getString(r, "host", "localhost"), JsonUtils.getInt(r, "port", SshClient.DEFAULT_PORT));
				WaitingTelnetClientCache.Connectable c = client.get(address);

				final String command = JsonUtils.getString(r, "command", "");
				JsonElement init = r.get("init");
				if (init != null) {
					for (JsonElement e : init.getAsJsonArray()) {
						c.init(e.getAsString());
					}
				}

				c.connect(new WaitingTelnetClientHandler() {
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
					public void launched(final String init, Callback callback) {
						callback.send(command, new WaitingTelnetClientHandler.Callback.SendCallback() {
							@Override
							public void failed(IOException e) {
								JsonObject r = new JsonObject();
								r.add("error", new JsonPrimitive(e.getMessage()));
								userCallback.handle(r);
							}
							@Override
							public void received(String text) {
								JsonObject r = new JsonObject();
								r.add("init", new JsonPrimitive(init));
								r.add("response", new JsonPrimitive(text));
								userCallback.handle(r);
							}
						});
					}
				});
			}
		});
	}
}
