package com.davfx.ninio.script;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.http.Http;
import com.davfx.ninio.http.HttpClient;
import com.davfx.ninio.http.HttpClientHandler;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpStatus;
import com.davfx.ninio.ping.Ping;
import com.davfx.ninio.ping.PingClientHandler;
import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.Result;
import com.davfx.ninio.snmp.Snmp;
import com.davfx.ninio.snmp.SnmpClientHandler;
import com.davfx.script.AsyncScriptFunction;
import com.davfx.script.ExecutorScriptRunner;
import com.davfx.script.ScriptRunner;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class ExtendedScriptRunner implements AutoCloseable {
	
	private final Queue queue;
	public final ScriptRunner runner;
	private final HttpClient http;

	public ExtendedScriptRunner() {
		queue = new Queue();

		runner = new QueueScriptRunner(queue, new ExecutorScriptRunner());
		http = new Http().withQueue(queue).client();
		
		runner.register("ping", new AsyncScriptFunction() {
			@Override
			public void call(JsonElement request, AsyncScriptFunction.Callback userCallback) {
				JsonObject r = request.getAsJsonObject();
				final String host = JsonUtils.getString(r, "host", Address.LOCALHOST);
				final AsyncScriptFunctionCallbackManager m = new AsyncScriptFunctionCallbackManager(userCallback);

				new Ping().withQueue(queue).ping(host, new PingClientHandler.Callback.PingCallback() {
					@Override
					public void failed(IOException e) {
						m.failed(e);
					}
					@Override
					public void pong(double time) {
						JsonPrimitive r = new JsonPrimitive(time);
						m.done(r);
					}
				});
			}
		});

		runner.register("snmp", new AsyncScriptFunction() {
			@Override
			public void call(JsonElement request, AsyncScriptFunction.Callback userCallback) {
				JsonObject r = request.getAsJsonObject();

				Address address = new Address(JsonUtils.getString(r, "host", Address.LOCALHOST), JsonUtils.getInt(r, "port", Snmp.DEFAULT_PORT));

				final AsyncScriptFunctionCallbackManager m = new AsyncScriptFunctionCallbackManager(userCallback);

				final String oidAsString = JsonUtils.getString(r, "oid", null);
				if (oidAsString == null) {
					m.failed(new IOException("OID cannot be null"));
					return;
				}
				final Oid oid;
				try {
					oid = new Oid(oidAsString);
				} catch (Exception e) {
					m.failed(new IOException("Invalid OID: " + oidAsString, e));
					return;
				}

				Snmp snmp = new Snmp().withQueue(queue).to(address);
				
				snmp.withCommunity(JsonUtils.getString(r, "community", null));

				JsonElement security = r.get("security");
				if (security != null) {
					JsonObject s = security.getAsJsonObject();
					JsonObject auth = s.get("authentication").getAsJsonObject();
					JsonObject priv = s.get("privacy").getAsJsonObject();
					snmp.withLoginPassword(
							JsonUtils.getString(auth, "login", null),
							JsonUtils.getString(auth, "password", null),
							JsonUtils.getString(auth, "method", null).toUpperCase(),
							JsonUtils.getString(priv, "login", null),
							JsonUtils.getString(priv, "password", null),
							JsonUtils.getString(priv, "method", null).toUpperCase()
					);
				}

				JsonElement timeout = r.get("timeout");
				if (timeout != null) {
					snmp.timeout(timeout.getAsDouble());
				}

				snmp.get(oid, new SnmpClientHandler.Callback.GetCallback() {
					private final JsonObject o = new JsonObject();
					@Override
					public void result(Result result) {
						// JsonObject o = new JsonObject();
						String resultOidAsString = result.getOid().toString();
						JsonPrimitive r = new JsonPrimitive(result.getValue());
						o.add(resultOidAsString, r); // result.getValue().asString()));
						// m.partially(o);
					}

					@Override
					public void close() {
						m.done(o);
						// m.done(new JsonObject());
					}

					@Override
					public void failed(IOException e) {
						m.failed(e);
					}
				});
			}
		});
		
		runner.register("http", new AsyncScriptFunction() {
			@Override
			public void call(JsonElement request, AsyncScriptFunction.Callback userCallback) {
				final AsyncScriptFunctionCallbackManager m = new AsyncScriptFunctionCallbackManager(userCallback);
				http.send(HttpRequest.of(JsonUtils.getString(request.getAsJsonObject(), "url", null)), new HttpClientHandler() {
					private ByteBufferContainer content = new ByteBufferContainer();
					
					@Override
					public void failed(IOException e) {
						m.failed(e);
					}
					
					@Override
					public void received(HttpResponse response) {
						if (response.status != HttpStatus.OK) {
							m.failed(new IOException("Status: " + response.status + ", Reason: " + response.reason));
						}
					}
					
					@Override
					public void ready(CloseableByteBufferHandler write) {
					}
					
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						content.add(buffer);
					}
					
					@Override
					public void close() {
						m.done(new JsonPrimitive(content.toString()));
					}
				});
			}
		});
	}

	@Override
	public void close() {
		runner.close();

		http.close();

		//TODO rm?
		queue.post(new Runnable() {
			@Override
			public void run() {
			}
		});

		queue.close();
	}
	
}