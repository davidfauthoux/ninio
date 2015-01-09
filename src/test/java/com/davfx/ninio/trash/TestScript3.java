package com.davfx.ninio.trash;

import java.io.IOException;
import java.util.Map;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Failable;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.proxy.ProxyClient;
import com.davfx.ninio.proxy.ProxyServer;
import com.davfx.ninio.script.BasicScript;
import com.davfx.ninio.script.SimpleScriptRunnerScriptRegister;
import com.davfx.ninio.script.SyncScriptFunction;
import com.davfx.ninio.script.util.AllAvailableScriptRunner;
import com.google.gson.JsonElement;

public class TestScript3 {
	public static void main(String[] args) throws Exception {
		System.out.println(System.getProperty("java.version"));
		Queue queue = new Queue();
		
		//new ProxyServer(6666, 10).start();
		ProxyClient proxy = new ProxyClient(new Address("localhost", 6666));
		AllAvailableScriptRunner r = new AllAvailableScriptRunner(queue);
		try {
			if (proxy !=null) {
				r.telnetConfigurator.override(proxy.socket());
				r.snmpConfigurator.override(proxy.datagram());
				r.pingConfigurator.override(proxy.ping());
			}
			
			for (int i = 0; i < 10; i++) {
				SimpleScriptRunnerScriptRegister rrr = r.runner();
	
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
							System.out.println("ERROR " + error.getAsString());
							return null;
						}
						for (Map.Entry<String, JsonElement> e : request.getAsJsonObject().get("result").getAsJsonObject().entrySet()) {
							System.out.println(e.getKey() + " = " + e.getValue().getAsString());
						}
						return null;
					}
				});

				rrr.register("log_ping", new SyncScriptFunction<JsonElement>() {
					@Override
					public JsonElement call(JsonElement request) {
						JsonElement error = request.getAsJsonObject().get("error");
						if (error != null) {
							System.out.println("ERROR " + error.getAsString());
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

				String OLD_WRAPPING = 
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
										"if (line && (line.length > 0)) _telnet_init_map[address].push({command:line,time:2});" +
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
										"_telnet({host:_host(address),port:_port(address),init:_telnet_init_map[address],command:line,time:2}, function(r) {" +
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
				rrr.eval(new BasicScript().append(OLD_WRAPPING + "__ip = '77.154.78.93'; "
						+ "var autoraise = function(severity, cause) { fire('autoraise	' + severity + '	' + cause); }; "
						+ "var autoraise_unreachable = function() { autoraise('UNREACHABLE', 'No cause available'); }; "
						+ "var autoraise_unavailable = function() { autoraise('UNAVAILABLE', 'No cause available'); }; "
						+ "var check_autoraise = function(ip) { autoraise_unreachable(); }; "
						
						+ "var telnetoutln = outln; "
						+ "__telnetcallback = function(err, r, ip) { "
						+ "if (err == null) { telnetoutln(r); } "
						+ "else { errln(err); check_autoraise(ip); }; }; "
						
						+ "__telnetidentifier = \"isadmin\" + __ip;"
						+ "telnet(null, __telnetidentifier).session().init(\"isadmin\").init(\"7360@SFR\"); "
						//+ "telnet(null, __telnetidentifier).init(\"\"); "
						//+ "telnet(null, __telnetidentifier).ready(); "
						+ "telnet(null, __telnetidentifier).command(\"show equipment ont optics-history\");"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				Thread.sleep(2000);
				rrr.eval(new BasicScript().append(OLD_WRAPPING + "__ip = '77.154.78.93'; "
						+ "var autoraise = function(severity, cause) { fire('autoraise	' + severity + '	' + cause); }; "
						+ "var autoraise_unreachable = function() { autoraise('UNREACHABLE', 'No cause available'); }; "
						+ "var autoraise_unavailable = function() { autoraise('UNAVAILABLE', 'No cause available'); }; "
						+ "var check_autoraise = function(ip) { autoraise_unreachable(); }; "
						
						+ "var telnetoutln = outln; "
						+ "__telnetcallback = function(err, r, ip) { "
						+ "if (err == null) { telnetoutln(r); } "
						+ "else { errln(err); check_autoraise(ip); }; }; "
						
						+ "__telnetidentifier = \"isadmin\" + __ip;"
						+ "telnet(null, __telnetidentifier).session().init(\"isadmin\").init(\"7360@SFR\"); "
						//+ "telnet(null, __telnetidentifier).init(\"\"); "
						//+ "telnet(null, __telnetidentifier).ready(); "
						+ "telnet(null, __telnetidentifier).command(\"show equipment ont optics-history\");"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});

				/*
				rrr.eval(new BasicScript().append("telnet({'host':'77.154.78.93', 'init':['isadmin', '7360@SFR'], command:'show equipment ont optics-history'}, log_telnet);"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				rrr.eval(new BasicScript().append("telnet({'host':'81.185.206.29', 'init':['isadmin', '7360@SFR'], command:'show equipment ont optics-history'}, log_telnet);"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				/*
				rrr.eval(new BasicScript().append("snmp({'host':'86.64.234.33', 'community':'rledacd', oid:'1.3.6.1.2.1.2.2.1.2'}, log_snmp);"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				/*
				rrr.eval(new BasicScript().append("ping({'host':'86.64.234.33'}, log_ping);"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				*/

				
				Thread.sleep(5000);
			}
			
			Thread.sleep(15000);
		} finally {
			r.close();
		}
		
		queue.close();
		System.exit(0);
	}
}
