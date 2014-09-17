package com.davfx.ninio.trash;

import java.io.IOException;

import com.davfx.ninio.common.Failable;
import com.davfx.ninio.script.BasicScript;
import com.davfx.ninio.script.SimpleScriptRunner;
import com.davfx.ninio.script.SyncScriptFunction;
import com.davfx.ninio.script.AsyncScriptFunction;
import com.davfx.ninio.script.SimpleScriptRunnerScriptRegister;
import com.davfx.ninio.script.util.AllAvailableScriptRunner;
import com.davfx.ninio.script.util.CallingEndScriptRunner;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class TestScript {
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
		try (AllAvailableScriptRunner r = new AllAvailableScriptRunner()) {
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
			//rrr.eval(new BasicScript().append("fff({'a':'aa'}, function(r) { log(r); });fff({'a':'aa'}, function(r) { log(r); });"));
			//rrr.eval(new BasicScript().append("log(ggg({'b':'bb'}));log(ggg({'b':'bb'}));"), null);
			
			//rr.eval(new BasicScript().append("http({'host':'google.com'}, function(r) { log(r['body']); });"));
			//rr.eval(new BasicScript().append("telnet({'init':['davidfauthoux','orod,ove'], 'command':'ls'}, function(r) { log(r['response']); });"));
			/*rr.eval(new BasicScript().append("telnet({'init':['davidfauthoux','orod,ove'], 'command':'ls'}, function(r) { log(r['init']); log(r['response']); });"), null);
			rr.eval(new BasicScript().append("ssh({'host':'172.17.10.31', 'init':['louser','pass'], 'command':'ls'}, function(r) { log(r['init']); log(r['response']); });"), new Failable() {
				@Override
				public void failed(IOException e) {
					e.printStackTrace();
				}
			});*/
			rr.eval(new BasicScript().append("ssh({'host':'172.17.10.31', 'init':['louser','pass'], 'command':'ls'}, function(r) { log(r['init']); log(r['response']); });"), new Failable() {
				@Override
				public void failed(IOException e) {
					e.printStackTrace();
				}
			});
			Thread.sleep(100000);
		}
	}

}
