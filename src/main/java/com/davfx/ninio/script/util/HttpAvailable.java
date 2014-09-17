package com.davfx.ninio.script.util;

import java.nio.ByteBuffer;

import com.davfx.ninio.http.Http;
import com.davfx.ninio.http.HttpClient;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.util.InMemoryPost;
import com.davfx.ninio.http.util.SimpleHttpClient;
import com.davfx.ninio.http.util.SimpleHttpClientHandler;
import com.davfx.ninio.script.AsyncScriptFunction;
import com.davfx.ninio.script.RegisteredFunctionsScriptRunner;
import com.davfx.util.ConfigUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class HttpAvailable {
	public static final String CALL_FUNCTION_NAME = ConfigUtils.load(HttpAvailable.class).getString("script.functions.http");

	private final HttpClient client;

	public HttpAvailable(HttpClient client) {
		this.client = client;
	}
	
	public RegisteredFunctionsScriptRunner register(RegisteredFunctionsScriptRunner runner) {
		runner.register(CALL_FUNCTION_NAME, new AsyncScriptFunction<JsonElement>() {
			private String getString(JsonObject r, String key, String defaultValue) {
				JsonElement e = r.get(key);
				if (e == null) {
					return defaultValue;
				}
				return e.getAsString();
			}
			private Integer getInt(JsonObject r, String key, Integer defaultValue) {
				JsonElement e = r.get(key);
				if (e == null) {
					return defaultValue;
				}
				return e.getAsInt();
			}
			private Boolean getBoolean(JsonObject r, String key, Boolean defaultValue) {
				JsonElement e = r.get(key);
				if (e == null) {
					return defaultValue;
				}
				return e.getAsBoolean();
			}
			@Override
			public void call(JsonElement request, AsyncScriptFunction.Callback<JsonElement> callback) {
				JsonObject r = request.getAsJsonObject();
				SimpleHttpClient c = new SimpleHttpClient().on(client);
				c.withMethod(HttpRequest.Method.valueOf(getString(r, "method", "GET").toUpperCase()));
				String post = getString(r, "post", null);
				if (post != null) {
					c.post(ByteBuffer.wrap(post.getBytes(Http.UTF8_CHARSET)));
				}
				
				String path = getString(r, "path", null);
				String host = getString(r, "host", "localhost");
				Integer port = getInt(r, "port", null);
				Boolean secure = getBoolean(r, "secure", null);
				c.on(path).withHost(host);
				if (port != null) {
					c.withPort(port);
				}
				if (secure != null) {
					c.secure(secure);
				}
				
				c.send(new SimpleHttpClientHandler() {
					@Override
					public void handle(int status, String reason, InMemoryPost body) {
						JsonObject r = new JsonObject();
						r.add("status", new JsonPrimitive(status));
						r.add("reason", new JsonPrimitive(reason));
						if (body != null) {
							r.add("body", new JsonPrimitive(body.toString()));
						}
						callback.handle(r);
					}
				});
			}
		});
		
		return runner;
	}
}
