package com.davfx.ninio.trash;

import java.io.IOException;
import java.util.Map;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Failable;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.proxy.ProxyClient;
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
		ProxyClient proxy = new ProxyClient(new Address("10.4.243.240", 9993));
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

				/*
				rrr.eval(new BasicScript().append("telnet({'host':'81.185.206.53', 'init':['isadmin', '7360@SFR'], command:'show equipment ont optics-history'}, log_telnet);"), new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				});
				*/
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
