package com.davfx.ninio.script.util;

import java.nio.ByteBuffer;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.http.Http;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.util.InMemoryPost;
import com.davfx.ninio.http.util.Parameters;
import com.davfx.ninio.http.util.SimpleHttpClient;
import com.davfx.ninio.http.util.SimpleHttpClientHandler;
import com.davfx.ninio.script.AsyncScriptFunction;
import com.davfx.ninio.script.ScriptRunner;
import com.davfx.util.ConfigUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class HttpAvailable {
	public static final String CALL_FUNCTION_NAME = ConfigUtils.load(HttpAvailable.class).getString("script.functions.http");

	private HttpAvailable() {
	}
	
	public static void register(ScriptRunner runner, final SimpleHttpClient client) {
		runner.register(CALL_FUNCTION_NAME, new AsyncScriptFunction() {
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
			public void call(JsonElement request, AsyncScriptFunction.Callback userCallback) {
				JsonObject r = request.getAsJsonObject();
				client.withMethod(HttpRequest.Method.valueOf(getString(r, "method", "GET").toUpperCase()));
				JsonElement post = r.get("post");
				if (post != null) {
					JsonObject postObject = post.getAsJsonObject();
					String postBody = getString(postObject, "body", null);
					String postContentTyString = getString(postObject, "type", Http.ContentType.JSON);
					if (postBody != null) {
						client.post(postContentTyString, ByteBuffer.wrap(postBody.getBytes(Http.UTF8_CHARSET)));
					}
				}
				
				Integer port = getInt(r, "port", null);
				Boolean secure = getBoolean(r, "secure", null);
				client.on(getString(r, "path", null)).withHost(getString(r, "host", Address.LOCALHOST));
				if (secure != null) {
					client.secure(secure);
				}
				if (port != null) {
					client.withPort(port);
				}
				
				final AsyncScriptFunctionCallbackManager m = new AsyncScriptFunctionCallbackManager(userCallback);
				client.send(new SimpleHttpClientHandler() {
					@Override
					public void handle(int status, String reason, Parameters parameters, InMemoryPost body) {
						JsonObject r = new JsonObject();
						r.add("status", new JsonPrimitive(status));
						r.add("reason", new JsonPrimitive(reason));
						if (body != null) {
							r.add("body", new JsonPrimitive(body.toString()));
						}
						m.done(r);
					}
				});
			}
		});
	}
}
