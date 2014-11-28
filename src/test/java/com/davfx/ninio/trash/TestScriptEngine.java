package com.davfx.ninio.trash;

import java.util.concurrent.ExecutorService;

import javax.script.ScriptEngine;

import com.davfx.ninio.script.ExecutorScriptRunner;

public class TestScriptEngine {
	private static final class A {
		private final String s;
		private final ExecutorService executorService;
		private final ScriptEngine engine;
		public A(String s, ExecutorService executorService, ScriptEngine engine) {
			System.out.println("ALLOC " + s);
			this.s = s;
			this.executorService = executorService;
			this.engine = engine;
		}
		@Override
		public String toString() {
			return "::" + s + "::";
		}
		@Override
		protected void finalize() {
			System.out.println("Finalize " + s);
		}
	}
	public static void main(String[] args) throws Exception {/*
		final ExecutorService executorService = Executors.newSingleThreadExecutor();
		final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
		while (true) {
			executorService.execute(new Runnable() {
				@Override
				public void run() {
					ScriptEngine scriptEngine = scriptEngineManager.getEngineByName("JavaScript");
					Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
					
					bindings.put("a", new A("1", executorService, scriptEngine));
					try {
						scriptEngine.eval("java.lang.System.out.println('aaa ' + a);");
					} catch (ScriptException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					bindings.remove("a");
				}
			});
			{
				A a = new A("2", null, null);
			}
			{
				A a = new A("3", null, null);
			}
			for (int i = 0; i < 1; i++) {
				System.gc();
				Thread.sleep(1000);
			}
		}*/
		
		final ExecutorScriptRunner ss = new ExecutorScriptRunner();
		while (true) {
			/*Deprecated
			ss.eval(Arrays.asList("call('toto', function(r) { java.lang.System.out.println('---> ' + r); });"), null, new AsyncScriptFunction<String>() {
				private final A a = new A("111", null, null);
				@Override
				public void call(final String request, Callback<String> callback) {
					ss.eval(Arrays.asList("call('tutu', function(r) { java.lang.System.out.println('---> ' + r); });"), null, new AsyncScriptFunction<String>() {
						private final A a = new A("222", null, null);
						@Override
						public void call(String request2, Callback<String> callback2) {
							callback2.handle("ECHO " + request + " " + request2);
						}
					}, null);
				}
			}, null);
			*/
			for (int i = 0; i < 1; i++) {
				//System.gc();
				Thread.sleep(1000);
			}
		}
	}
}
