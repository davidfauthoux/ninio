package com.davfx.ninio.trash;

import java.io.IOException;
import java.util.Map;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Failable;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.proxy.ProxyClient;
import com.davfx.ninio.script.ScriptRunner;
import com.davfx.ninio.script.SyncScriptFunction;
import com.davfx.ninio.script.util.AllAvailableScriptRunner;
import com.google.gson.JsonElement;

public class TestScript4__testFloat {
	public static void main(String[] args) throws Exception {
		System.out.println(System.getProperty("java.version"));
		Queue queue = new Queue();
		
		//new ProxyServer(9993, 10).start();
		//ProxyClient proxy =  new ProxyClient(new Address("127.0.0.1", 9993));
		//ProxyClient proxy =  new ProxyClient(new Address("172.17.10.20", 8888));
		ProxyClient proxy =  new ProxyClient(new Address("172.17.10.16", 8888));
		AllAvailableScriptRunner r = new AllAvailableScriptRunner(queue);
		try {
			if (proxy !=null) {
				r.telnetConfigurator.override(proxy.socket());
				r.snmpConfigurator.override(proxy.datagram());
				r.pingConfigurator.override(proxy.ping());
			}
			
			/*
			int ethMin = 129;
			int ethMax = 140;
			int ipMin = 1;
			int ipMax = 254;
			final int total = (ethMax - ethMin + 1) * (ipMax - ipMin + 1);
			final int[] count = new int[] {0};
			for (int eth = ethMin; eth <= ethMax; eth++) {
				for (int j = ipMin; j <= ipMax; j++) {
					final int j$ = j;
					*/
					ScriptRunner.Engine rr = r.runner();
	
					rr.register("log_telnet", new SyncScriptFunction() {
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
	
					rr.register("log_snmp", new SyncScriptFunction() {
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
							System.out.println(Thread.currentThread() + " --- DONE SNMP");
							return null;
						}
					});
	
					rr.register("log_ping", new SyncScriptFunction() {
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
	
					rr.register("outln", new SyncScriptFunction() {
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
	
					rr.register("errln", new SyncScriptFunction() {
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
	
					rr.register("fire", new SyncScriptFunction() {
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
	
					/*
					//rr.eval("snmp({'host':'127.0.0.1', 'community':'public', oid:'1.3.6.1.2.1.2.2.1.2'}, log_snmp);", new Failable() {
					rr.eval("snmp({'host':'127.0.0.1', 'community':'public', oid:'1.3.6.1.2.1.1.4.0" + / *1.3.6.1.2.1.2.2.1.2* / "'}, log_snmp);", new Failable() {
						@Override
						public void failed(IOException e) {
							e.printStackTrace();
						}
					});
					rr.eval("snmp({'host':'127.0.0.1', 'community':'public', oid:'1.3.6.1.2.1.1.4.0" + / *1.3.6.1.2.1.2.2.1.2* / "'}, log_snmp);", new Failable() {
						@Override
						public void failed(IOException e) {
							e.printStackTrace();
						}
					});
					*/
				
					rr.eval("snmp({'host':'10.224.1.1', 'community':'public', oid:'1.1.1'}, log_snmp);", new Failable() {
						@Override
						public void failed(IOException e) {
							e.printStackTrace();
						}
					}, new Runnable() {
						@Override
						public void run() {
							System.out.println(Thread.currentThread() + " ---------END");
						}
					});
					/*
					rr.eval("snmp({'host':'10.0." + eth + "." + j + "', 'community':'public', oid:'1.1'}, log_snmp);", new Failable() {
						@Override
						public void failed(IOException e) {
							e.printStackTrace();
						}
					}, new Runnable() {
						@Override
						public void run() {
							count[0]++;
							System.out.println(Thread.currentThread() + " ---------END " + j$ + "---------- " + count[0] + " < " + total);
						}
					});*/
					
					/*
					rr.eval("aaa = 666;log('aaa='+aaa);", new Failable() {
						@Override
						public void failed(IOException e) {
							e.printStackTrace();
						}
					});
					rr.eval("aaa++;log('aaa='+aaa);", new Failable() {
						@Override
						public void failed(IOException e) {
							e.printStackTrace();
						}
					});
					rr.eval("snmp({'host':'172.17.0.1', 'community':'public', oid:'1.3.6.1.2.1.2.2.1.2'}, log_snmp);", new Failable() {
						@Override
						public void failed(IOException e) {
							e.printStackTrace();
						}
					});
					//Thread.sleep(2 * 60*1000);
					rr.eval("snmp({'host':'172.17.0.1', 'community':'public', oid:'1.3.6.1.2.1.2.2.1.2'}, log_snmp);", new Failable() {
						@Override
						public void failed(IOException e) {
							e.printStackTrace();
						}
					});
					rr.eval("ping({'host':'172.17.0.1'}, log_ping);", new Failable() {
						@Override
						public void failed(IOException e) {
							e.printStackTrace();
						}
					});
					rr.eval("telnet({'init':[{command:'davidfauthoux',time:0.25},{command:'orod,ove',time:0.25}], 'command':'ls',time:0.25}, log_telnet);", new Failable() {
						@Override
						public void failed(IOException e) {
							e.printStackTrace();
						}
					});
					rr.eval("telnet({'init':[{cut:'.*login\\\\:\\\\s'},{command:'davidfauthoux',cut:'.*Password\\\\:'},{command:'orod,ove',cut:'.*\\\\$\\\\s'}], 'command':'ls',cut:'.*\\\\$\\\\s'}, log_telnet);", new Failable() {
						@Override
						public void failed(IOException e) {
							e.printStackTrace();
						}
					});
					rr.eval("telnet({'init':[{cut:'.*login\\\\:\\\\s'},{command:'davidfauthoux',cut:'.*Password\\\\:'},{command:'orod,ove',cut:'.*\\\\$\\\\s'}], 'command':'ls',cut:'.*\\\\$\\\\s'}, log_telnet);", new Failable() {
						@Override
						public void failed(IOException e) {
							e.printStackTrace();
						}
					});
					*/
					
					/*
					rr.eval("telnet({'init':[{cut:'.*login\\\\:\\\\s'},{command:'davidfauthoux',cut:'.*Password\\\\:'},{command:'orod,ove',cut:'.*\\\\$\\\\s'}], 'command':'ls',cut:'.*\\\\$\\\\s'}, log_telnet);", new Failable() {
						@Override
						public void failed(IOException e) {
							e.printStackTrace();
						}
					});
					rr.eval("telnet({'init':[{cut:'.*login\\\\:\\\\s'},{command:'davidfauthoux',cut:'.*Password\\\\:'},{command:'orod,ove',cut:'.*\\\\$\\\\s'}], 'command':'ls',cut:'.*\\\\$\\\\s'}, log_telnet);", new Failable() {
						@Override
						public void failed(IOException e) {
							e.printStackTrace();
						}
					});
					*/
				/*
				}
			}
			*/

			Thread.sleep(500000);
		} finally {
			r.close();
		}
		
		queue.close();
		System.exit(0);
	}
}
