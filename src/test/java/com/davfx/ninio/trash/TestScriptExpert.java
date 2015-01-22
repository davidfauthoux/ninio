package com.davfx.ninio.trash;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Failable;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.proxy.ProxyClient;
import com.davfx.ninio.script.BasicScript;
import com.davfx.ninio.script.SimpleScriptRunnerScriptRegister;
import com.davfx.ninio.script.SyncScriptFunction;
import com.davfx.ninio.script.util.AllAvailableScriptRunner;
import com.davfx.ninio.script.util.CallingEndScriptRunner;
import com.davfx.util.PrependIterable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public class TestScriptExpert {
	public static void main(String[] args) throws Exception {
		System.out.println(System.getProperty("java.version"));
		Queue queue = new Queue();
		
		//new ProxyServer(9993, 10).start();
		ProxyClient proxy =  new ProxyClient(new Address("10.4.243.240", 9993));
		//ProxyClient proxy =  new ProxyClient(new Address("127.0.0.1", 6667));
		AllAvailableScriptRunner r = new AllAvailableScriptRunner(queue);
		try {
			if (proxy !=null) {
				r.telnetConfigurator.override(proxy.socket());
				r.snmpConfigurator.override(proxy.datagram());
				r.pingConfigurator.override(proxy.ping());
			}
			
			for (int i = 0; i < 10; i++) {
				SimpleScriptRunnerScriptRegister rrr = r.runner();

				rrr.register("now", new SyncScriptFunction<JsonElement>() {
					public JsonElement call(JsonElement request) {
						return new JsonPrimitive(System.currentTimeMillis() / 1000d);
					}
				});
				

				rrr.register("log_telnet", new SyncScriptFunction<JsonElement>() {
					@Override
					public JsonElement call(JsonElement request) {
						JsonElement error = request.getAsJsonObject().get("error");
						if (error != null) {
							System.out.println("ERROR " + error.getAsString());
							return null;
						}
						System.out.println(request.getAsJsonObject().get("result").getAsJsonObject().get("response").getAsString());
						return null;
					}
				});

				rrr.register("log_snmp", new SyncScriptFunction<JsonElement>() {
					@Override
					public JsonElement call(JsonElement request) {
						JsonElement error = request.getAsJsonObject().get("error");
						if (error != null) {
							System.out.println("ERROR SNMP " + error.getAsString());
							return null;
						}
						for (Map.Entry<String, JsonElement> e : request.getAsJsonObject().get("result").getAsJsonObject().entrySet()) {
							System.out.println(e.getKey() + " = " + e.getValue().getAsString());
						}
						System.out.println("DONE SNMP");
						return null;
					}
				});

				rrr.register("log_ping", new SyncScriptFunction<JsonElement>() {
					@Override
					public JsonElement call(JsonElement request) {
						JsonElement error = request.getAsJsonObject().get("error");
						if (error != null) {
							System.out.println("ERROR PING " + error.getAsString());
							return null;
						}
						System.out.println("time = " + request.getAsJsonObject().get("result").getAsDouble());
						return null;
					}
				});

				rrr.register("outln", new SyncScriptFunction<JsonElement>() {
					@Override
					public JsonElement call(JsonElement request) {
						if (request == null) {
							System.out.println("--------> NULL");
							return null;
						}
						System.out.println("------> " + request.toString());
						return null;
					}
				});

				rrr.register("errln", new SyncScriptFunction<JsonElement>() {
					@Override
					public JsonElement call(JsonElement request) {
						if (request == null) {
							System.out.println("ERR --------> NULL");
							return null;
						}
						System.out.println("ERR ------> " + request.toString());
						return null;
					}
				});

				rrr.register("fire", new SyncScriptFunction<JsonElement>() {
					@Override
					public JsonElement call(JsonElement request) {
						if (request == null) {
							System.out.println("FIRE --------> NULL");
							return null;
						}
						System.out.println("FIRE ------> " + request.toString());
						return null;
					}
				});

				rrr.register(CallingEndScriptRunner.END_FUNCTION_NAME, new SyncScriptFunction<JsonElement>() {
					@Override
					public JsonElement call(JsonElement request) {
						System.out.println("END ***");
						return null;
					}
				});

				new CallingEndScriptRunner(rrr).eval(new PrependIterable<String>(OLD_WRAPPING, new BasicScript().append(AUTORAISE_WRAPPING + "snmp('public', '172.17.0.1').oid('1.3.6.1.2.1.2.2.1.2');")), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
/*
				new CallingEndScriptRunner(rrr).eval(new PrependIterable<String>(OLD_WRAPPING, new BasicScript().append(AUTORAISE_WRAPPING + "ping('172.17.0.1').ping();")), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
*/				
				Thread.sleep(500000);
			}
			
			Thread.sleep(15000);
		} finally {
			r.close();
		}
		
		queue.close();
		System.exit(0);
	}
	
	public static final String AUTORAISE_WRAPPING =
			//TODO Remove...
			"function each(o, callback) {" +
					"if (o instanceof Array) {" +
						"var f = function(i, callback) {" +
							"if (i < o.length) {" +
								"callback(o[i], i);" +
								"f(i + 1, callback);" +
							"}" +
						"};" +
						"f(0, callback);" +
					"} else {" +
						"var k;" +
						"for (k in o) {" +
							"callback(o[k], k);" +
						"}" +
					"}" +
				"};" +
			"var json = JSON.stringify;" +
			//TODO ...to here
			
			
			"var snmpoutln = function(oid, value) {" +
				"if (value == null) {" +
					"return;" +
				"}" +
				"outln('value:' + oid + '\\t' + json(value) + '\\t' + now());" +
			"};" +
			"var oidreset = function(oid, resetOid) {" +
				"outln('reset:' + oid + '\\t' + resetOid);" +
			"};" +
			"var oidalias = function(oid, alias) {" +
				"outln('alias:' + oid + '\\t' + json(alias));" +
			"};" +
			"__snmpcallback = function(err, r) {" +
				"each(r, function(value, oid) {" +
					"snmpoutln(oid, value);" +
				"});" +
			"};" +

			"var telnetoutln = outln;" +
			"__telnetcallback = function(err, r) {" +
				"telnetoutln(r);" +
			"};" +
			
			"var pingoutln = function(pingIp, time) {" +
				"outln(pingIp + '\\t0\\t' + time);" + // Status is left to '0' for now
			"};" +
			"__pingcallback = function(err, r) {" +
				"each(r, function(v, pingIp) {" +
					"pingoutln(pingIp, v['time']);" +
				"});" +
			"};" +

			//
			
			"var __telnet = telnet;" +
			"var __snmp = snmp;" +
			"var __ping = ping;" +
			
			"var __host = function(address) {" +
				"var n = address.indexOf(':');" +
				"if (n < 0) { return address; }" +
				"return address.substring(0, n);" +
			"};" +
			
			"var __wrap = function(address, callback) {" +
				"return function(error, result) {" +
					"if (error) {" +
						"__ping(__host(address)).ping(function(pingError, pingResult) {" +
							"fire('autoraise\\t' + (pingError ? 'UNREACHABLE' : 'UNAVAILABLE') + '\\tNo cause available (' + error + ')');" +
						"});" +
					"} else {" +
						"callback(error, result);" +
					"}" +
				"}" +
			"};" +

			"telnet = function(address) {" +
				"var _t = __telnet(address);" +
				"var t = {" +
					"session:" +
						"function(callback) {" +
							"callback = callback || __telnetcallback;" +
							"return _t.session(__wrap(address, callback));" +
						"}," +
					"init:" +
						"function(line, callback) {" +
							"callback = callback || __telnetcallback;" +
							"return _t.init(line, __wrap(address, callback));" +
						"}," +
					"ready:" +
						"function() {" +
							"return _t.ready();" +
						"}," +
					"command:" +
						"function(line, callback) {" +
							"callback = callback || __telnetcallback;" +
							"return _t.command(line, __wrap(address, callback));" +
						"}" +
				"};" +
				"return t;" +
			"};" +
				
			"snmp = function(community, address) {" +
				"var _t = __snmp(community, address);" +
				"var t = {" +
					"oid:" +
						"function(oid, callback) {" +
							"callback = callback || __snmpcallback;" +
							"return _t.oid(oid, __wrap(address, callback));" +
						"}" +
				"};" +
				"return t;" +
			"};" +
				
			"ping = function(address) {" +
				"var _t = __ping(address);" +
				"var t = {" +
					"ping:" +
						"function(callback) {" +
							"callback = callback || __pingcallback;" +
							"return _t.ping(__wrap(address, callback));" +
						"}" +
				"};" +
				"return t;" +
			"};";

	private static final double TELNET_INIT_RESPONSE_TIME = 2d;
	private static final double TELNET_COMMAND_RESPONSE_TIME = 30d;
	
	public static final String OLD_WRAPPING = 
		"var __ip = 'localhost';" +
		"var __telnetcallback = function(e, r) {};" +
		"var __snmpcallback = function(e, r) {};" +
		"var __pingcallback = function(e, r) {};" +

		"var _telnet = telnet;" +
		"var _snmp = snmp;" +
		"var _ping = ping;" +
		"var _telnet_init_map = {};" +

		"var _host = function(address) {" +
			"var n = address.indexOf(':');" +
			"if (n < 0) { return address; }" +
			"return address.substring(0, n);" +
		"};" +
		"var _port = function(address) {" +
			"var n = address.indexOf(':');" +
			"if (n < 0) { return null; }" +
			"return 0 + address.substring(n + 1);" +
		"};" +
		"telnet = function(address) {" +
			"address = address || __ip;" +
			"if (!_telnet_init_map[address]) _telnet_init_map[address] = [];" +
			"var t = {" +
				"session:" +
					"function(callback) {" +
						"callback = callback || __telnetcallback;" +
						"callback();" +
						"return t;" +
					"}," +
				"init:" +
					"function(line, callback) {" +
						"if (line && (line.length > 0)) _telnet_init_map[address].push({'command':line, 'time':" + TELNET_INIT_RESPONSE_TIME + "});" +
						"callback = callback || __telnetcallback;" +
						"callback();" +
						"return t;" +
					"}," +
				"ready:" +
					"function() {" +
						"return t;" +
					"}," +
				"command:" +
					"function(line, callback) {" +
						"callback = callback || __telnetcallback;" +
						"_telnet({host:_host(address),port:_port(address),init:_telnet_init_map[address],command:line,time:" + TELNET_COMMAND_RESPONSE_TIME + "}, function(r) {" +
							"if (!r) return;" +
							"if (r['error']) {" +
								"callback('Error [' + r['error'] + ']');" +
							"} else {" +
								"var rr = r['result'];" +
								"callback(null, rr['init'] + rr['response']);" +
							"}" +
						"});" +
						"return t;" +
					"}" +
			"};" +
			"return t;" +
		"};" +
			
		"snmp = function(community, address) {" +
			"address = address || __ip;" +
			"var t = {" +
				"oid:" +
					"function(oid, callback) {" +
						"callback = callback || __snmpcallback;" +
						"_snmp({host:_host(address),port:_port(address),community:community,oid:oid}, function(r) {" +
							"if (!r) return;" +
							"if (r['error']) {" +
								"callback('Error [' + r['error'] + ']');" +
							"} else {" +
								"var rr = r['result'];" +
								"callback(null, rr);" +
							"}" +
						"});" +
						"return t;" +
					"}" +
			"};" +
			"return t;" +
		"};" +
			
		"ping = function(address) {" +
			"address = address || __ip;" +
			"var t = {" +
				"ping:" +
					"function(callback) {" +
						"callback = callback || __pingcallback;" +
						"_ping({host:_host(address),port:_port(address)}, function(r) {" +
							"if (!r) return;" +
							"if (r['error']) {" +
								"callback('Error [' + r['error'] + ']');" +
							"} else {" +
								"var rr = r['result'];" +
								"var m = {}; m[address] = {'time': rr}; callback(null, m);" +
							"}" +
						"});" +
						"return t;" +
					"}" +
			"};" +
			"return t;" +
		"};";
/*
	private static final double TELNET_INIT_RESPONSE_TIME = 1d;
	private static final double TELNET_COMMAND_RESPONSE_TIME = 2d;
	
	public static final String OLD_WRAPPING = 
		"var __ip = 'localhost';" +
		"var __telnetcallback = function(e, r) {};" +
		"var __snmpcallback = function(e, r) {};" +
		"var __pingcallback = function(e, r) {};" +

		"var _telnet = telnet;" +
		"var _snmp = snmp;" +
		"var _ping = ping;" +
		"var _telnet_init_map = {};" +

		"var _host = function(address) {" +
			"var n = address.indexOf(':');" +
			"if (n < 0) { return address; }" +
			"return address.substring(0, n);" +
		"};" +
		"var _port = function(address) {" +
			"var n = address.indexOf(':');" +
			"if (n < 0) { return null; }" +
			"return 0 + address.substring(n + 1);" +
		"};" +
		"telnet = function(address) {" +
			"address = address || __ip;" +
			"if (!_telnet_init_map[address]) _telnet_init_map[address] = [];" +
			"var t = {" +
				"session:" +
					"function(callback) {" +
						"callback = callback || __telnetcallback;" +
						"callback();" +
						"return t;" +
					"}," +
				"init:" +
					"function(line, callback) {" +
						"if (line && (line.length > 0)) _telnet_init_map[address].push({'command':line, 'time':" + TELNET_INIT_RESPONSE_TIME + "});" +
						"callback = callback || __telnetcallback;" +
						"callback();" +
						"return t;" +
					"}," +
				"ready:" +
					"function() {" +
						"return t;" +
					"}," +
				"command:" +
					"function(line, callback) {" +
						"callback = callback || __telnetcallback;" +
						"_telnet({host:_host(address),port:_port(address),init:_telnet_init_map[address],command:line,time:" + TELNET_COMMAND_RESPONSE_TIME + "}, function(r) {" +
							"if (!r) return;" +
							"if (r['error']) {" +
								"callback('Error [' + r['error'] + ']');" +
							"} else {" +
								"var rr = r['result'];" +
								"callback(null, rr['init'] + rr['response']);" +
							"}" +
						"});" +
						"return t;" +
					"}" +
			"};" +
			"return t;" +
		"};" +
			
		"snmp = function(community, address) {" +
			"address = address || __ip;" +
			"var t = {" +
				"oid:" +
					"function(oid, callback) {" +
						"callback = callback || __snmpcallback;" +
						"_snmp({host:_host(address),port:_port(address),community:community,oid:oid}, function(r) {" +
							"if (!r) return;" +
							"if (r['error']) {" +
								"callback('Error [' + r['error'] + ']');" +
							"} else {" +
								"var rr = r['result'];" +
								"callback(null, rr);" +
							"}" +
						"});" +
						"return t;" +
					"}" +
			"};" +
			"return t;" +
		"};" +
			
		"ping = function(address) {" +
			"address = address || __ip;" +
			"var t = {" +
				"ping:" +
					"function(callback) {" +
						"callback = callback || __pingcallback;" +
						"_ping({host:_host(address)}, function(r) {" +
							"if (!r) return;" +
							"if (r['error']) {" +
								"callback('Error [' + r['error'] + ']');" +
							"} else {" +
								"var rr = r['result'];" +
								"var m = {}; m[address] = {'time': rr}; log('--->' + json(m));callback(null, m);" +
							"}" +
						"});" +
						"return t;" +
					"}" +
			"};" +
			"return t;" +
		"};";
		*/
}

final class PollScriptUtil {

    public static final Logger LOGGER = LoggerFactory.getLogger(PollScriptUtil.class);

	private PollScriptUtil() {
	}
	
	public static String createScriptHeader(String ipAddress) {
		StringBuilder rawScript = new StringBuilder();
		rawScript.append("__ip = '" + ipAddress + "'; ");
		rawScript.append("var autoraise = function(severity, cause) { fire('" + QdfxCommons.AUTORAISE_COMMAND + QdfxCommons.SEPARATOR + "' + severity + '" + QdfxCommons.SEPARATOR + "' + cause); }; ");
		rawScript.append("var autoraise_unreachable = function() { autoraise('UNREACHABLE', 'No cause available'); }; ");
		rawScript.append("var autoraise_unavailable = function() { autoraise('UNAVAILABLE', 'No cause available'); }; ");
		rawScript.append("var check_autoraise = function(ip) { autoraise_unreachable(); " +
//			"ping(ip).ping(function(err, r) { " +
//				"if (err == null) { " +
//					"var ok = false; " +
//					"each(r, function(v, pingIp) { " +
//						"each(v, function(a) { " +
//							"if (a['status'] == 0) { " +
//								"ok = true; " +
//							"} " +
//						"}); " +
//					"}); " +
//					"if (ok) { " +
//						"autoraise_unavailable(); " +
//					"} else { " +
//						"autoraise_unreachable(); " +
//					"} " +
//				"} else { " +
//					"errln(err); " +
//					"autoraise_unreachable(); " +
//				"} " +
//			"}); " +
		"}; ");
		return rawScript.toString();
	}
	
	private static JsonObject buildMeta(String metadataAsString, String key) {
		if (metadataAsString == null) {
			return null;
		}
		JsonElement e = new JsonParser().parse(metadataAsString).getAsJsonObject().get(key);
		if (e == null) {
			return null;
		}
		return e.getAsJsonObject();
	}
	private static JsonElement getMeta(JsonObject meta, JsonObject defaultMeta, String key) {
		if (meta == null) {
			if (defaultMeta == null) {
				if (LOGGER.isWarnEnabled())
					LOGGER.warn("Missing specification");
				return null;
			}
			return defaultMeta.get(key);
		}
		
		JsonElement e = meta.get(key);
		if (e == null) {
			if (defaultMeta == null) {
				return null;
			}
			e = defaultMeta.get(key);
		}
		return e;
	}

	public static String createScript(String protocol, String objectAsString, String metadataAsString, String defaultMetadataAsString) throws IllegalArgumentException {
		String script;
		switch (protocol) {
			case "telnet":
				script = createTelnetScript(objectAsString, metadataAsString, defaultMetadataAsString);
				break;
			case "snmp":
				script = createSnmpScript(objectAsString, metadataAsString, defaultMetadataAsString);
				break;
			case "ping":
				script = createPingScript(objectAsString, metadataAsString, defaultMetadataAsString);
				break;

			default:
				throw new IllegalArgumentException( "Unknown protocol \"" + protocol + "\".");
		}
		return script;
	}

	private static String createSnmpScript(String snmpObjectAsString, String metadataAsString, String defaultMetadataAsString) {
		StringBuilder rawScript = new StringBuilder();
		rawScript.append("var snmpoutln = function(oid, value) { " +
											"if (value == null) { " +
												"return; " +
											"}; " +
											"outln('value:' + oid + '\\t' + json(value) + '\\t' + now()); " +
										"}; ");
		rawScript.append("var oidreset = function(oid, resetOid) { " +
											"outln('reset:' + oid + '\\t' + resetOid); " +
										"}; ");
		rawScript.append("var oidalias = function(oid, alias) { " +
											"outln('alias:' + oid + '\\t' + json(alias)); " +
										"}; ");
		rawScript.append("__snmpcallback = function(err, r, ip) { " +
							"if (err == null) { " +
								"each(r, function(value, oid) { " +
									"snmpoutln(oid, value); " +
								"}); " +
							"} else { " +
								"errln(err); " +
								"check_autoraise(ip); " +
							"} " +
						"}; ");
		
		JsonObject meta = buildMeta(metadataAsString, "snmp");
		JsonObject defaultMeta = buildMeta(defaultMetadataAsString, "snmp");
		String community = getMeta(meta, defaultMeta, "community").getAsString();
		rawScript.append("var _community = '' + attribute(\"community\");");
		rawScript.append("if (_community == null || _community == 'null' || _community == undefined || _community == 'undefined')" +
							"_community = " + JavascriptUtils.encode(community) + ";" +
						"}; ");
		
		Set<String> oids = new HashSet<String>();
		
		for (JsonElement e : new JsonParser().parse(snmpObjectAsString).getAsJsonArray()) {
			JsonObject o = e.getAsJsonObject();
			
			if (o.get("simple") != null) {
				JsonObject oo = o.get("simple").getAsJsonObject();
				String oid = oo.get("oid").getAsString();
				if (oo.get("reset") != null) {
					String resetOid = oo.get("reset").getAsString();
					rawScript.append("oidreset('" + oid + "', '" + resetOid + "'); ");
					oids.add(resetOid);
				}
				if (oo.get("alias") != null) {
					String alias = oo.get("alias").getAsString();
					rawScript.append("oidalias('" + oid + "', '" + alias + "'); ");
				}
				oids.add(oid);
			}
			
			if (o.get("expert") != null) {
				rawScript.append(o.get("expert").getAsString()).append(QdfxCommons.EOL);
			}
		}

		for (String oid : oids) {
			rawScript.append("snmp(_community).oid('" + oid + "'); ");
		}
		
		return rawScript.toString();
	}

	private static String createTelnetScript(String telnetObjectAsString, String metadataAsString, String defaultMetadataAsString) {
		StringBuilder rawScript = new StringBuilder();
		
		rawScript.append("var telnetoutln = outln; ");
		rawScript.append("__telnetcallback = function(err, r, ip) { " +
							"if (err == null) { " +
								"telnetoutln(r); " +
							"} else { " +
								"errln(err); " +
								"check_autoraise(ip); " +
							"}; " +
						"}; ");

		JsonObject meta = buildMeta(metadataAsString, "telnet");
		JsonObject defaultMeta = buildMeta(defaultMetadataAsString, "telnet");

		String login = JavascriptUtils.encode(getMeta(meta, defaultMeta, "login").getAsString());
		String identifier = login;

		rawScript.append("__telnetidentifier = " + identifier + " + __ip;");
		rawScript.append("telnet(null, __telnetidentifier)"
			+ ".session()"
			+ ".init(" + login + ")"
			+ ".init(" + JavascriptUtils.encode(getMeta(meta, defaultMeta, "password").getAsString()) + "); ");
		for (JsonElement v : getMeta(meta, defaultMeta, "init").getAsJsonArray()) {
			rawScript.append("telnet(null, __telnetidentifier).init(" + JavascriptUtils.encode(v.getAsString()) + "); ");
		}
		rawScript.append("telnet(null, __telnetidentifier).ready(); ");
		for (JsonElement e : new JsonParser().parse(telnetObjectAsString).getAsJsonArray()) {
			JsonObject o = e.getAsJsonObject();
			if (o.get("command") != null) {
				rawScript.append("telnet(null, __telnetidentifier).command(" + JavascriptUtils.encode(o.get("command").getAsString()) + "); ");
			}

			if (o.get("expert") != null) {
				rawScript.append(o.get("expert").getAsString()).append(QdfxCommons.EOL);
			}
		}
		return rawScript.toString();
	}

	private static String createPingScript(String pingObjectAsString, String metadataAsString, String defaultMetadataAsString) {
		StringBuilder rawScript = new StringBuilder();
		
		rawScript.append("var pingoutln = function(pingIp, status, time) { " +
							"outln(pingIp + '\\t' + status + ((time == null) ? '' : ('\\t' + time))); " +
						"}; ");
		rawScript.append("__pingcallback = function(err, r, ip) { " +
							"if (err == null) { " +
								"each(r, function(v, pingIp) { " +
									"each(v, function(a) { " +
										"pingoutln(pingIp, a['status'], a['time']); " +
									"}); " +
								"}); " +
							"} else { " +
								"errln(err); " +
								"check_autoraise(ip); " +
							"} " +
						"}; ");

		for (JsonElement e : new JsonParser().parse(pingObjectAsString).getAsJsonArray()) {
			JsonObject o = e.getAsJsonObject();
			if (o.get("ping") != null) {
				rawScript.append("ping().ping(); ");
			}
			
			if (o.get("expert") != null) {
				rawScript.append(o.get("expert").getAsString()).append(QdfxCommons.EOL);
			}
		}
		return rawScript.toString();
	}

}

final class QdfxCommons {
	public static final String AUTORAISE_COMMAND = "autoraise";
	public static final String SEPARATOR = "\t";
	public static final String EOL = "\n";
}

final class JavascriptUtils {
    public static String encode(String s) {
        if (s == null) {
            return null;
        }
        return new JsonPrimitive(s).toString();
    }
}
