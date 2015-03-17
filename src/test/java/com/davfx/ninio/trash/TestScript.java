package com.davfx.ninio.trash;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Failable;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.proxy.ProxyClient;
import com.davfx.ninio.proxy.ProxyServer;
import com.davfx.ninio.script.AsyncScriptFunction;
import com.davfx.ninio.script.BasicScript;
import com.davfx.ninio.script.SimpleScriptRunner;
import com.davfx.ninio.script.SimpleScriptRunnerScriptRegister;
import com.davfx.ninio.script.SyncScriptFunction;
import com.davfx.ninio.script.util.AllAvailableScriptRunner;
import com.davfx.ninio.script.util.CallingEndScriptRunner;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class TestScript {
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
		/*new SingleExecutorScriptRunner().eval("var a = ['uu']; call('TOTO' + a, function(r) { var rrr = r.toString(); call(rrr, function(rr) { " + SingleExecutorScriptRunner.class.getCanonicalName() + ".log(rr); }); });", new ScriptFunction<String>() {
		//new ScriptQueueRunner().eval("call('TOTO', function(r) { call('--' + r, function(rr) { com.davfx.ninio.script.ScriptQueueRunner.console(rr); }); });", new ScriptFunction() {
			
			@Override
			public void call(String request, Callback<String> callback) {
				System.out.println("IN FUNCTION");
				callback.handle("ECHO " + request);
			}
		});*/
		/*new QueueScriptRunner<JsonElement>(new Queue(), new JsonScriptRunner(new SingleExecutorScriptRunner())).eval("call({'a':'aa'}, function(r) { call(r, function(rr) { " + SingleExecutorScriptRunner.class.getCanonicalName() + ".log(r['a']); }); });", new ScriptFunction<JsonElement>() {
			@Override
			public void call(JsonElement request, Callback<JsonElement> callback) {
				System.out.println("IN FUNCTION");
				callback.handle(request);
			}
		});*/

		//new ProxyServer(6666, 2).start();
		/*new ProxyServer(7777)
		.override(ProxyUtils.SOCKET_TYPE, Forward.forward(new Address("localhost", 6666)))
		.override(ProxyUtils.DATAGRAM_TYPE, Forward.forward(new Address("localhost", 6666)))
		.start();*/
		/*
		new ProxyServer(8888)
		.override(ProxyUtils.SOCKET_TYPE, Forward.forward(new Address("localhost", 6666)))
		.override(ProxyUtils.DATAGRAM_TYPE, Forward.forward(new Address("localhost", 6666)))
		.start();
		ProxyClient proxy = new ProxyClient(new Address("localhost", 8888));
		*/
		ProxyClient proxy = new ProxyClient(new Address("localhost", 6666));

		if ("a".equals("a" + "")) {
		try (AllAvailableScriptRunner r = new AllAvailableScriptRunner(new Queue())) {
			r.telnetConfigurator.override(proxy.socket());
			r.snmpConfigurator.override(proxy.datagram());
			r.pingConfigurator.override(proxy.of("ping"));
			
			for (int i = 0; i < 10; i++) {
				System.out.println("-------------------------");
				System.out.println("-------------------------");
				System.out.println("-------------------------");
				System.out.println("-------------------------");
				for (int j = 0; j < 1; j++) {
					A aa = new A("outside");
			SimpleScriptRunnerScriptRegister rr = r.runner();
			rr.register("fff", new AsyncScriptFunction<JsonElement>() {
				@Override
				public void call(JsonElement request, Callback<JsonElement> callback) {
					System.out.println(Thread.currentThread() + " IN ASYNC FUNCTION");
					callback.handle(request.getAsJsonObject().get("a"));
				}
			});
			rr.register("ggg", new SyncScriptFunction<JsonElement>() {
				@Override
				public JsonElement call(JsonElement request) {
					System.out.println(Thread.currentThread() + " IN SYNC FUNCTION");
					return new JsonPrimitive("ggg" + request.getAsJsonObject().get("b").getAsString());
				}
			});
			rr.register("end", new SyncScriptFunction<JsonElement>() {
				@Override
				public JsonElement call(JsonElement request) {
					System.out.println(Thread.currentThread() + " END");
					return null;
				}
			});
			SimpleScriptRunner rrr = new CallingEndScriptRunner(rr);
			rr.register("myfunc", new AsyncScriptFunction<JsonElement>() {
				A a = new A("in myfunc");
				@Override
				public void call(
						JsonElement request,
						AsyncScriptFunction.Callback<JsonElement> callback) {
					callback.handle(new JsonPrimitive("ECHO " + request.getAsString()));
				}
			});
			rr.register("myfunc2", new SyncScriptFunction<JsonElement>() {
				A a = new A("in myfunc2");
				@Override
				public JsonElement call(JsonElement request) {
					return new JsonPrimitive("ECHO " + request.getAsString());
				}
			});
			/*
			rrr.eval(new BasicScript().append("myfunc('titi', function(r) { log(myfunc2('toto ' + r)); });"), new Failable() {
				@Override
				public void failed(IOException e) {
					e.printStackTrace();
				}
			});
			*/
			
			//rrr.eval(new BasicScript().append("snmp({'host':'172.17.10.184', 'community':'', 'oid':'1.3.6.1.2.1.2.2.1.2'}, function(r) { myfunc(JSON.stringify(r), function(rr) { log(rr); }); });"), new Failable() {
			rrr.eval(new BasicScript().append("snmp({'host':'172.17.10.184', 'community':'', 'oid':'1.3.6.1.2.1.2.2.1.2'}, function(r) { log('1.3.6.1.2.1.2.2.1.2'); log(r); });"), new Failable() {
				@Override
				public void failed(IOException e) {
					e.printStackTrace();
				}
			});
			
			//rrr.eval(new BasicScript().append("fff({'a':'aa'}, function(r) { log(r); });fff({'a':'aa'}, function(r) { log(r); });"));
			//rrr.eval(new BasicScript().append("log(ggg({'b':'bb'}));log(ggg({'b':'bb'}));"), null);
			
			//rr.eval(new BasicScript().append("http({'host':'google.com'}, function(r) { log(r['body']); });"));
			//rr.eval(new BasicScript().append("telnet({'init':['davidfauthoux',''], 'command':'ls'}, function(r) { log(r['response']); });"));
			/*rr.eval(new BasicScript().append("telnet({'init':['davidfauthoux',''], 'command':'ls'}, function(r) { log(r['init']); log(r['response']); });"), null);
			rr.eval(new BasicScript().append("ssh({'host':'172.17.10.31', 'init':['louser','pass'], 'command':'ls'}, function(r) { log(r['init']); log(r['response']); });"), new Failable() {
				@Override
				public void failed(IOException e) {
					e.printStackTrace();
				}
			});*/
			/*
			rr.eval(new BasicScript().append("ssh({'host':'172.17.10.31', 'init':['louser','pass'], 'command':'ls'}, function(r) { log(r['init']); log(r['response']); });"), new Failable() {
				@Override
				public void failed(IOException e) {
					e.printStackTrace();
				}
			});*/
			/*
				System.gc();
				System.out.println(Runtime.getRuntime().freeMemory());
				Thread.sleep(1000);
				*/
			System.out.println("------ WAITING----------");
			Thread.sleep(10000);
			System.out.println("------ WAITED----------");
				}
			}
			System.out.println("------ END----------");
			System.out.println("------ END----------");
			System.out.println("------ END----------");
			System.out.println("------ END----------");
			for (int i = 0; i < 100; i++) {
				System.gc();
				Thread.sleep(1000);
				}
			Thread.sleep(3000);
		}
		}

		System.out.println("----------!!!!!--------");
		
		for (int i = 0; i < 100; i++) {
		System.gc();
		System.out.println(Runtime.getRuntime().freeMemory());
		Thread.sleep(1000);
		}
	}

}
