package com.davfx.ninio.script.util;

import java.io.IOException;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.script.AsyncScriptFunction;
import com.davfx.ninio.script.ScriptRunner;
import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.Result;
import com.davfx.ninio.snmp.SnmpClientConfigurator;
import com.davfx.ninio.snmp.SnmpClientHandler;
import com.davfx.ninio.snmp.util.SnmpClientCache;
import com.davfx.util.ConfigUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class SnmpAvailable {
	public static final String CALL_FUNCTION_NAME = ConfigUtils.load(SnmpAvailable.class).getString("script.functions.snmp");

	private SnmpAvailable() {
	}
	
	public static void register(ScriptRunner runner, final SnmpClientCache client, final Cache cache) {
		runner.register(CALL_FUNCTION_NAME, new AsyncScriptFunction() {
			@Override
			public void call(JsonElement request, final AsyncScriptFunction.Callback userCallback) {
				JsonObject r = request.getAsJsonObject();
				
				Address address = new Address(JsonUtils.getString(r, "host", "localhost"), JsonUtils.getInt(r, "port", SnmpClientConfigurator.DEFAULT_PORT));

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

				final Cache.ForAddressCache internalCache;
				if (cache == null) {
					internalCache = null;
				} else {
					internalCache = cache.get(address);
					if (!internalCache.register(oidAsString, m)) { // Check already waiting, register if so, set waiting if not
						return;
					}
				}
				
				SnmpClientCache.Connectable c = client.get(address);
				c.withCommunity(JsonUtils.getString(r, "community", null));
				
				JsonElement security = r.get("security");
				if (security != null) {
					JsonObject s = security.getAsJsonObject();
					JsonObject auth = s.get("authentication").getAsJsonObject();
					JsonObject priv = s.get("privacy").getAsJsonObject();
					c.withLoginPassword(
							JsonUtils.getString(auth, "login", null),
							JsonUtils.getString(auth, "password", null),
							JsonUtils.getString(auth, "method", null).toUpperCase(),
							JsonUtils.getString(priv, "login", null),
							JsonUtils.getString(priv, "password", null),
							JsonUtils.getString(priv, "method", null).toUpperCase()
					);
				}

				c.connect(new SnmpClientHandler() {
					@Override
					public void failed(IOException e) {
						m.failed(e);
						if (internalCache != null) {
							internalCache.failed(oidAsString, e);
						}
					}
					@Override
					public void close() {
						m.close();
						if (internalCache != null) {
							internalCache.close(oidAsString);
						}
					}
					@Override
					public void launched(final Callback callback) {
						callback.get(oid, new SnmpClientHandler.Callback.GetCallback() {
							private final JsonObject o = new JsonObject();
							@Override
							public void result(Result result) {
								// JsonObject o = new JsonObject();
								String resultOidAsString = result.getOid().toString();
								JsonPrimitive r = new JsonPrimitive(result.getValue());
								o.add(resultOidAsString, r); // result.getValue().asString()));
								if (internalCache != null) {
									internalCache.add(resultOidAsString, r);
								}
								// m.partially(o);
							}
							
							@Override
							public void close() {
								m.done(o);
								// m.done(new JsonObject());
								if (internalCache != null) {
									internalCache.done(oidAsString, o);
								}
								callback.close();
							}
							
							@Override
							public void failed(IOException e) {
								m.failed(e);
								if (internalCache != null) {
									internalCache.failed(oidAsString, e);
								}
								callback.close();
							}
							/*%%%
							private void add(JsonObject r, Result result) {
								r.add(result.getOid().toString(), new JsonPrimitive(result.getValue().asString()));
							}
							@Override
							public void finished(Iterable<Result> results) {
								JsonObject r = new JsonObject();
								for (Result result : results) {
									add(r, result);
								}
								
								callback.close();
								userCallback.handle(r);
							}
							@Override
							public void finished(Result result) {
								JsonObject r = new JsonObject();
								add(r, result);

								callback.close();
								userCallback.handle(r);
							}
							*/
						});
					}
				});
			}
		});
	}
}
