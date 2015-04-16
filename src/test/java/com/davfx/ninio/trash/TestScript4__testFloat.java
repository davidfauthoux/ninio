package com.davfx.ninio.trash;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Failable;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.proxy.ProxyClient;
import com.davfx.ninio.proxy.ProxyServer;
import com.davfx.ninio.script.BasicScript;
import com.davfx.ninio.script.SyncScriptFunction;
import com.davfx.ninio.script.util.AllAvailableRunner;
import com.davfx.ninio.script.util.AllAvailableScriptRunner;
import com.google.gson.JsonElement;

public class TestScript4__testFloat {
	public static void main(String[] args) throws Exception {
		System.out.println(System.getProperty("java.version"));
		Queue queue = new Queue();
		
		//new ProxyServer(9993, 10).start();
		//ProxyClient proxy =  new ProxyClient(new Address("127.0.0.1", 9993));
		ProxyClient proxy =  null;//new ProxyClient(new Address("10.4.243.240", 9993));
		AllAvailableScriptRunner r = new AllAvailableScriptRunner(queue);
		try {
			if (proxy !=null) {
				r.telnetConfigurator.override(proxy.socket());
				r.snmpConfigurator.override(proxy.datagram());
				r.pingConfigurator.override(proxy.ping());
			}
			
			for (AllAvailableRunner rr : r.runners()) {
				rr.register("log_telnet");
				rr.register("log_snmp");
				rr.register("log_ping");
				rr.register("outln");
				rr.register("errln");
				rr.register("fire");
				List<String> s = new LinkedList<String>();
				s.add("log('INIT OK');");
				rr.prepare(s, new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
			}
			
			for (int i = 0; i < 10; i++) {
				AllAvailableRunner rr = r.runner();
	
				rr.link(new Runnable() {
					@Override
					public void run() {
						System.out.println(Thread.currentThread() + " ---------END----------");
					}
				});
				
				rr.link("log_telnet", new SyncScriptFunction<JsonElement>() {
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

				rr.link("log_snmp", new SyncScriptFunction<JsonElement>() {
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

				rr.link("log_ping", new SyncScriptFunction<JsonElement>() {
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

				rr.link("outln", new SyncScriptFunction<JsonElement>() {
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

				rr.link("errln", new SyncScriptFunction<JsonElement>() {
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

				rr.link("fire", new SyncScriptFunction<JsonElement>() {
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

				//rr.eval(new BasicScript().append("snmp({'host':'127.0.0.1', 'community':'public', oid:'1.3.6.1.2.1.2.2.1.2'}, log_snmp);"), new Failable() {
				rr.eval(new BasicScript().append("snmp({'host':'127.0.0.1', 'community':'public', oid:'1.3.6.1.2.1.1.4.0" + /*1.3.6.1.2.1.2.2.1.2*/ "'}, log_snmp);"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				rr.eval(new BasicScript().append("snmp({'host':'127.0.0.1', 'community':'public', oid:'1.3.6.1.2.1.1.4.0" + /*1.3.6.1.2.1.2.2.1.2*/ "'}, log_snmp);"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				
				/*
				rr.eval(new BasicScript().append("aaa = 666;log('aaa='+aaa);"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				rr.eval(new BasicScript().append("aaa++;log('aaa='+aaa);"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				rr.eval(new BasicScript().append("snmp({'host':'172.17.0.1', 'community':'public', oid:'1.3.6.1.2.1.2.2.1.2'}, log_snmp);"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				//Thread.sleep(2 * 60*1000);
				rr.eval(new BasicScript().append("snmp({'host':'172.17.0.1', 'community':'public', oid:'1.3.6.1.2.1.2.2.1.2'}, log_snmp);"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				rr.eval(new BasicScript().append("ping({'host':'172.17.0.1'}, log_ping);"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				rr.eval(new BasicScript().append("telnet({'init':[{command:'davidfauthoux',time:0.25},{command:'orod,ove',time:0.25}], 'command':'ls',time:0.25}, log_telnet);"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				rr.eval(new BasicScript().append("telnet({'init':[{cut:'.*login\\\\:\\\\s'},{command:'davidfauthoux',cut:'.*Password\\\\:'},{command:'orod,ove',cut:'.*\\\\$\\\\s'}], 'command':'ls',cut:'.*\\\\$\\\\s'}, log_telnet);"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				rr.eval(new BasicScript().append("telnet({'init':[{cut:'.*login\\\\:\\\\s'},{command:'davidfauthoux',cut:'.*Password\\\\:'},{command:'orod,ove',cut:'.*\\\\$\\\\s'}], 'command':'ls',cut:'.*\\\\$\\\\s'}, log_telnet);"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				*/
				rr.eval(new BasicScript().append("telnet({'init':[{cut:'.*login\\\\:\\\\s'},{command:'davidfauthoux',cut:'.*Password\\\\:'},{command:'orod,ove',cut:'.*\\\\$\\\\s'}], 'command':'ls',cut:'.*\\\\$\\\\s'}, log_telnet);"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				rr.eval(new BasicScript().append("telnet({'init':[{cut:'.*login\\\\:\\\\s'},{command:'davidfauthoux',cut:'.*Password\\\\:'},{command:'orod,ove',cut:'.*\\\\$\\\\s'}], 'command':'ls',cut:'.*\\\\$\\\\s'}, log_telnet);"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				
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
