package com.davfx.ninio.trash;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

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

public class TestScript2 {
	private static final class A {
		String s;
		static AtomicInteger COUNT = new AtomicInteger(0);
		A(String s) {
			System.out.println("ALLOC " + s + " " + COUNT.incrementAndGet());
			this.s = s;
		}
		@Override
		protected void finalize() {
			System.out.println("FINALIZE " + s + " " + COUNT.decrementAndGet());
		}
	}
	public static void main(String[] args) throws Exception {
		Queue queue = new Queue();
		
		new ProxyServer(6666, 10).start();
		ProxyClient proxy = new ProxyClient(new Address("localhost", 6666));
		
		//proxy =null;

		if ("0".equals(String.valueOf(0))) {
		AllAvailableScriptRunner r = new AllAvailableScriptRunner(queue);
		try {
			if (proxy !=null) {
			r.telnetConfigurator.override(proxy.socket());
			r.snmpConfigurator.override(proxy.datagram());
			r.pingConfigurator.override(proxy.of("ping"));
			}

			A aa = new A("outside");
			
			SimpleScriptRunnerScriptRegister rrr = r.runner();

			rrr.register("logg", new SyncScriptFunction<JsonElement>() {
				@Override
				public JsonElement call(JsonElement request) {
					System.out.println(Thread.currentThread() + " LOG " + request.toString());
					return null;
				}
			});
			rrr.eval(new BasicScript().append("snmp({'host':'172.17.10.184', 'community':'logway', 'oid':'1.3.6.1.2.1.2.2.1.2'}, function(r) { logg('.2:' + JSON.stringify(r)); });"), new Failable() {
				@Override
				public void failed(IOException e) {
					e.printStackTrace();
				}
			});
			//Thread.sleep(100);
			rrr.eval(new BasicScript().append("snmp({'host':'172.17.10.184', 'community':'logway', 'oid':'1.3.6.1.2.1.2.2.1.3'}, function(r) { logg('.3:' + JSON.stringify(r)); });"), new Failable() {
				@Override
				public void failed(IOException e) {
					e.printStackTrace();
				}
			});
			//Thread.sleep(100);
			rrr.eval(new BasicScript().append("snmp({'host':'172.17.10.184', 'community':'logway', 'oid':'1.3.6.1.2.1.2.2.1.2'}, function(r) { log('++.2:' + JSON.stringify(r)); });snmp({'host':'172.17.10.184', 'community':'logway', 'oid':'1.3.6.1.2.1.2.2.1.2'}, function(r) { log('+.2:' + JSON.stringify(r)); });"), new Failable() {
				@Override
				public void failed(IOException e) {
					e.printStackTrace();
				}
			});
/*			Iterable<String> sss = new BasicScript().append("var fff = function() { log('titi'); fff(); };fff();\n// while (true) { log('toto'); }\n");
			final long timeoutInJavascript = (long) (5d * 1000d);

			sss = Iterables.transform(sss, new Function<String, String>() {
				@Override
				public String apply(String input) {
					return inject(input, "__checkTimeout();");
				}
			});

			sss = new PrependIterable<String>(

					"var __timeFromStart = new Date().getTime();" +
					"function __checkTimeout() {" +
						"var t = new Date().getTime();" +
						"if ((t - __timeFromStart) > " + timeoutInJavascript + ") {" +
							"throw 'Timeout';" +
						"}" +
					"};"
					
					, sss);
			rrr.eval(sss,
					new Failable() {
				@Override
				public void failed(IOException e) {
					e.printStackTrace();
				}
			});*/


			System.out.println("------ END----------");
			for (int i = 0; i < 3000; i++) {
				System.gc();
				Thread.sleep(1000);
			}
		} finally {
			r.close();
		}
		}

		System.out.println("----------!!!!!--------");
		
		for (int i = 0; i < 100; i++) {
			System.gc();
			System.out.println(Runtime.getRuntime().freeMemory());
			Thread.sleep(1000);
		}
	}

	public static String inject(String script, String part) {
		for (String keyword : new String[] { "{", ";" }) {
			StringBuilder b = new StringBuilder();
			int i = 0;
			while (true) {
				int k = script.indexOf(keyword, i);
				if (k < 0) {
					b.append(script.substring(i));
					break;
				}
				
				b.append(script.substring(i, k + 1));
				b.append(part);
				
				i = k + 1;
			}
			script = b.toString();
		}
		return script;
	}

}
