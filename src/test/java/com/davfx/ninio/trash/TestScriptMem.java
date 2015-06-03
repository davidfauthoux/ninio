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
import com.davfx.ninio.script.ScriptRunner;
import com.davfx.ninio.script.SyncScriptFunction;
import com.davfx.ninio.script.util.AllAvailableScriptRunner;
import com.google.gson.JsonElement;

public class TestScriptMem {
	public static void main(String[] args) throws Exception {
		System.out.println(System.getProperty("java.version"));
		/*
		ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName("JavaScript");
		Bindings bindings = new SimpleBindings();
		scriptEngine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
		bindings.put("a", "aa");
		// scriptEngine.eval("java.lang.System.out.println('aaa='+a);var b= 'bb';");
		scriptEngine.eval("var b;var f = function() { java.lang.System.out.println('bbb='+b);};");
		bindings.put("b", "bbbbbbb");
		scriptEngine.eval("f();");
		System.exit(0);
		
		System.out.println(bindings.get("a"));
		System.out.println(bindings.get("b"));
		Bindings b = new SimpleBindings();
		b.putAll(bindings);
		scriptEngine.setBindings(b, ScriptContext.ENGINE_SCOPE);
		scriptEngine.eval("java.lang.System.out.println('aaa='+a);var b= 'bb';");
		System.exit(0);
		
*/

		Queue queue = new Queue();
		
		new ProxyServer(9993, 10).start();
		ProxyClient proxy =  new ProxyClient(new Address("127.0.0.1", 9993));

		foo(proxy, queue);

		System.out.println("--------------------------------------- DONE ------------");

		for (int i = 0; i < 100; i++) {
			free();
			Thread.sleep(1000);
		}
		
		System.out.println("--------------------------------------- QUEUE CLOSE ------------");
		
		queue.close();

		Thread.sleep(100000);

		System.exit(0);

	}
	
	private static void foo(ProxyClient proxy, Queue queue) throws Exception {
		AllAvailableScriptRunner r = new AllAvailableScriptRunner(queue);
		try {
			if (proxy !=null) {
				r.telnetConfigurator.override(proxy.socket());
				r.snmpConfigurator.override(proxy.datagram());
				r.pingConfigurator.override(proxy.ping());
			}
			for (ScriptRunner rr : r.runners()) {
				rr.prepare("log('INIT OK');", new Failable() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
				}, null);
			}

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

			for (int i = 0; i < 1; i++) {
				/*
				rr.eval(new BasicScript().append("aaaaa='aaaaabbbbb';"), new Failable() {
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
				rr.eval(new BasicScript().append("java.lang.System.out.println('**************** = ' + aaaaa);"), new Failable() {
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
				*/
				long t = System.nanoTime();
				if (false)
				rr.eval("snmp({'host':'127.0.0.1', 'community':'public', oid:'1.3.6.1.2.1.1.4.0'}, log_snmp);", new Failable() {
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
				t = System.nanoTime() - t;
				System.out.println("=========== " + (t / (1000d * 1000d)) + " ms");
				/*
				rr.eval(new BasicScript().append("snmp({'host':'127.0.0.2', 'community':'public', oid:'1.3.6.1.2.1.1.4.0'}, function(r, err) { snmp({'host':'127.0.0.1', 'community':'public', oid:'1.3.6.1.2.1.1.4.0'}, function(r, err) { snmp({'host':'127.0.0.1', 'community':'public', oid:'1.3.6.1.2.1.1.4.0'}, log_snmp);ping({'host':'172.17.0.2'}, log_ping);var ooo=null;var aaa=ooo.indexOf('ee');    java.lang.System.out.println('r='+JSON.stringify(r));});ping({'host':'172.17.0.2'}, log_ping);java.lang.System.out.println('r='+JSON.stringify(r));}); "), new Failable() {
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
				*/
				if (false)
				rr.eval("telnet({'init':[{cut:'.*login\\\\:\\\\s'},{command:'davidfauthoux',cut:'.*Password\\\\:'},{command:'orod,ove',cut:'.*\\\\$\\\\s'}], 'command':'ls',cut:'.*\\\\$\\\\s'}, log_telnet);", new Failable() {
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
				//if (false)
				rr.eval("ping({'host':'172.17.0.1'}, log_ping);", new Failable() {
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
			}

			Thread.sleep(20000);
		} finally {
			r.close();
		}
	}
	
	private static void free() {
		List<byte[]> l = new LinkedList<>();
		for (int i = 0; i < 100; i++) {
			l.add(new byte[1000000]);
		}
	}
}
