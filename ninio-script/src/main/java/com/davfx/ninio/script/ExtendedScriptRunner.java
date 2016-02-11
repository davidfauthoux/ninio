package com.davfx.ninio.script;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.DatagramReadyFactory;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.http.Http;
import com.davfx.ninio.http.HttpMethod;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpStatus;
import com.davfx.ninio.http.InMemoryBuffers;
import com.davfx.ninio.ping.Ping;
import com.davfx.ninio.ping.PingClientHandler;
import com.davfx.ninio.snmp.AuthRemoteSpecification;
import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.Result;
import com.davfx.ninio.snmp.Snmp;
import com.davfx.ninio.snmp.SnmpClientCache;
import com.davfx.ninio.snmp.SnmpClientHandler;
import com.davfx.ninio.ssh.Ssh;
import com.davfx.ninio.telnet.Telnet;
import com.davfx.ninio.telnet.TelnetSharing;
import com.davfx.ninio.telnet.TelnetSharingHandler;
import com.davfx.script.AsyncScriptFunction;
import com.davfx.script.ExecutorScriptRunner;
import com.davfx.script.ScriptRunner;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.net.HttpHeaders;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class ExtendedScriptRunner implements AutoCloseable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedScriptRunner.class);
	
	private static final Config CONFIG = ConfigFactory.load(ExtendedScriptRunner.class.getClassLoader());
	private static final int MAX_SNMP_CONNECTION_CACHE_SIZE = CONFIG.getInt("ninio.script.snmp.cache.max");

	public final ScriptRunner runner;
	private final Http http;
	private final TelnetSharing telnet;
	private final TelnetSharing ssh;
	
	private final SnmpClientCache snmpClientCache;

	public ExtendedScriptRunner(final Queue queue, ReadyFactory tcpReadyFactory, final ReadyFactory udpReadyFactory, final ReadyFactory pingReadyFactory) {
		runner = new QueueScriptRunner(queue, new ExecutorScriptRunner());

		http = new Http();
		http.override(tcpReadyFactory).withQueue(queue);
		
		telnet = new TelnetSharing();
		telnet.override(tcpReadyFactory).withQueue(queue);
		ssh = new TelnetSharing();
		ssh.override(tcpReadyFactory).withQueue(queue);

		snmpClientCache = (MAX_SNMP_CONNECTION_CACHE_SIZE == 0) ?
				null :
				new SnmpClientCache(queue, (udpReadyFactory == null) ? new DatagramReadyFactory(queue) : udpReadyFactory, MAX_SNMP_CONNECTION_CACHE_SIZE);
		
		//
		
		runner.register("ping", new AsyncScriptFunction() {
			@Override
			public void call(JsonElement request, AsyncScriptFunction.Callback userCallback) {
				JsonObject r = request.getAsJsonObject();
				String host = JsonUtils.getString(r, "host");
				final AsyncScriptFunctionCallbackManager m = new AsyncScriptFunctionCallbackManager(userCallback);

				new Ping().override(pingReadyFactory).withQueue(queue).ping(host, new PingClientHandler.Callback.PingCallback() {
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

				Address address = new Address(JsonUtils.getString(r, "host"), JsonUtils.getInt(r, "port", Snmp.DEFAULT_PORT));

				final AsyncScriptFunctionCallbackManager m = new AsyncScriptFunctionCallbackManager(userCallback);

				String oidAsString = JsonUtils.getString(r, "oid");
				if (oidAsString == null) {
					m.failed(new IOException("OID cannot be null"));
					return;
				}
				Oid oid;
				try {
					oid = new Oid(oidAsString);
				} catch (Exception e) {
					m.failed(new IOException("Invalid OID: " + oidAsString, e));
					return;
				}

				Snmp snmp = new Snmp().withCache(snmpClientCache).override(udpReadyFactory).withQueue(queue).to(address);
				
				String community = JsonUtils.getString(r, "community", null);
				if (community != null) {
					snmp.withCommunity(community);
				}

				JsonElement security = r.get("security");
				if (security != null) {
					JsonObject s = security.getAsJsonObject();
					JsonObject auth = s.get("authentication").getAsJsonObject();
					JsonObject priv = s.get("privacy").getAsJsonObject();
					snmp.withAuth(new AuthRemoteSpecification(
							JsonUtils.getString(auth, "login", null),
							JsonUtils.getString(auth, "password", null),
							JsonUtils.getString(auth, "method", null).toUpperCase(),
							JsonUtils.getString(priv, "login", null),
							JsonUtils.getString(priv, "password", null),
							JsonUtils.getString(priv, "method", null).toUpperCase()
						));
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
				JsonObject r = request.getAsJsonObject();
				String url = JsonUtils.getString(r, "url");
				
				ImmutableMultimap.Builder<String, String> headers = ImmutableMultimap.builder();

				JsonElement postObject = r.get("post");
				final ByteBuffer post;
				if (postObject == null) {
					post = null;
				} else {
					JsonObject o = postObject.getAsJsonObject();
					post = ByteBuffer.wrap(JsonUtils.getString(o, "data").getBytes(Charsets.UTF_8));
					String contentType = JsonUtils.getString(o, "contentType", null);
					if (contentType != null) {
						headers.put(HttpHeaders.CONTENT_TYPE, contentType);
					}
				}
				
				final AsyncScriptFunctionCallbackManager m = new AsyncScriptFunctionCallbackManager(userCallback);
				http.send(HttpRequest.of(url, (post == null) ? HttpMethod.GET : HttpMethod.POST, headers.build()), post, new Http.InMemoryHandler() {
					@Override
					public void failed(IOException e) {
						m.failed(e);
					}
					
					@Override
					public void handle(HttpResponse response, InMemoryBuffers content) {
						if (response.status != HttpStatus.OK) {
							m.failed(new IOException("Status: " + response.status + ", Reason: " + response.reason));
							return;
						}
						m.done(new JsonPrimitive(content.toString()));
					}
				});
			}
		});

		runner.register("telnet", new AsyncScriptFunction() {
			@Override
			public void call(JsonElement request, AsyncScriptFunction.Callback userCallback) {
				JsonObject r = request.getAsJsonObject();
				
				Address address = new Address(JsonUtils.getString(r, "host"), JsonUtils.getInt(r, "port", Telnet.DEFAULT_PORT));

				TelnetSharingHandler handler = telnet.client(Telnet.sharing(), address);
				boolean first = true;
				for (JsonElement e : r.get("init").getAsJsonArray()) {
					JsonObject o = e.getAsJsonObject();
					handler.init(first ? JsonUtils.getString(o, "command", null) : JsonUtils.getString(o, "command"), JsonUtils.getString(o, "prompt"), new TelnetSharingHandler.Callback() {
						@Override
						public void failed(IOException e) {
							LOGGER.warn("Init failed", e);
						}
						@Override
						public void handle(String response) {
							// Ignored
							LOGGER.debug("Init response: {}", response);
						}
					});
					first = false;
				}

				String command = JsonUtils.getString(r, "command");
				String prompt = JsonUtils.getString(r, "prompt");

				final AsyncScriptFunctionCallbackManager m = new AsyncScriptFunctionCallbackManager(userCallback);
				handler.write(command, prompt, new TelnetSharingHandler.Callback() {
					@Override
					public void failed(IOException e) {
						m.failed(e);
					}
					
					@Override
					public void handle(String response) {
						m.done(new JsonPrimitive(response));
					}
				});
			}
		});

		runner.register("ssh", new AsyncScriptFunction() {
			@Override
			public void call(JsonElement request, AsyncScriptFunction.Callback userCallback) {
				JsonObject r = request.getAsJsonObject();
				
				Address address = new Address(JsonUtils.getString(r, "host"), JsonUtils.getInt(r, "port", Ssh.DEFAULT_PORT));

				TelnetSharingHandler handler = telnet.client(Ssh.sharing(JsonUtils.getString(r, "login"), JsonUtils.getString(r, "password")), address);
				boolean first = true;
				for (JsonElement e : r.get("init").getAsJsonArray()) {
					JsonObject o = e.getAsJsonObject();
					handler.init(first ? JsonUtils.getString(o, "command", null) : JsonUtils.getString(o, "command"), JsonUtils.getString(o, "prompt"), new TelnetSharingHandler.Callback() {
						@Override
						public void failed(IOException e) {
							LOGGER.warn("Init failed", e);
						}
						@Override
						public void handle(String response) {
							// Ignored
							LOGGER.debug("Init response: {}", response);
						}
					});
					first = false;
				}

				String command = JsonUtils.getString(r, "command");
				String prompt = JsonUtils.getString(r, "prompt");

				final AsyncScriptFunctionCallbackManager m = new AsyncScriptFunctionCallbackManager(userCallback);
				handler.write(command, prompt, new TelnetSharingHandler.Callback() {
					@Override
					public void failed(IOException e) {
						m.failed(e);
					}
					
					@Override
					public void handle(String response) {
						m.done(new JsonPrimitive(response));
					}
				});
			}
		});
	}

	@Override
	public void close() {
		if (snmpClientCache != null) {
			snmpClientCache.close();
		}
		runner.close();

		http.close();
		telnet.close();
		ssh.close();
	}
	
}