package com.davfx.ninio.script;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;
import com.google.common.collect.ImmutableMap;

public class ScriptTest {

	@Test
	public void testSimpleSync() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<Object, Exception> lock = new Lock<>();
		Wait wait = new Wait();
		
		engine.register("syncEcho1", new SyncScriptFunction<Map<String, String>, Map<String, String>>() {
			@Override
			public Map<String, String> call(Map<String,String> request) {
				Map<String, String> m = new HashMap<>();
				m.put("out", request.get("message"));
				return m;
			}
		});
		
		engine.register("syncEcho2", new SyncScriptFunction<Map<String, String>, Map<String, String>>() {
			@Override
			public Map<String, String> call(Map<String,String> request) {
				lock.set(request.get("out"));
				return request;
			}
		});
		
		ScriptRunner.Engine subEngine = engine.sub();
		subEngine.eval("var echoed = syncEcho2(syncEcho1({'message':'bb'}));", null, new WaitLockScriptRunnerEnd(wait, lock));
		
		Assertions.assertThat(lock.waitFor().toString()).isEqualTo("bb");
		wait.waitFor();
	}

	@Test
	public void testArraySync() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<String, Exception> lock = new Lock<>();
		Wait wait = new Wait();
		
		engine.register("syncEcho1", new SyncScriptFunction<Map<Object, String>, String[]>() {
			@Override
			public String[] call(Map<Object, String> request) {
				System.out.println(request.getClass());
				String[] r = new String[request.size() + 1];
				for (Map.Entry<Object, String> e : request.entrySet()) {
					System.out.println(e.getKey() + " " + e.getKey().getClass() + " -> " + e.getValue());
					r[Integer.parseInt(e.getKey().toString())] = e.getValue();
				}
				r[r.length - 1] = "+";
				return r;
			}
		});
		
		engine.register("syncEcho2", new SyncScriptFunction<String, String>() {
			@Override
			public String call(String request) {
				lock.set(request);
				return request;
			}
		});
		
		ScriptRunner.Engine subEngine = engine.sub();
		subEngine.eval("java.lang.System.out.println(syncEcho1(['aa', 'bb']) instanceof Array);syncEcho2(syncEcho1(['aa', 'bb'])[2]);", null, new WaitLockScriptRunnerEnd(wait, lock));
		
		Assertions.assertThat(lock.waitFor()).isEqualTo("+");
		wait.waitFor();
	}

	@Test
	public void testSimpleAsync() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<Object, Exception> lock = new Lock<>();
		Wait wait = new Wait();

		engine.register("syncEcho", new SyncScriptFunction<Object, Object>() {
			@Override
			public Object call(Object request) {
				lock.set(request);
				return request;
			}
		});
		engine.register("asyncEcho", new AsyncScriptFunction<Object, Object>() {
			@Override
			public void call(Object request, AsyncScriptFunction.Callback<Object> callback) {
				callback.handle(request);
				callback.done();
			}
		});
		
		ScriptRunner.Engine subEngine = engine.sub();
		subEngine.eval("asyncEcho('aaa', function(r) { syncEcho(r); });", null, new WaitLockScriptRunnerEnd(wait, lock));
		
		Assertions.assertThat(lock.waitFor()).isEqualTo("aaa");
		wait.waitFor();
	}

	@Test
	public void testSimpleSyncWithDouble() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<Double, Exception> lock = new Lock<>();
		Wait wait = new Wait();
		
		engine.register("syncEcho", new SyncScriptFunction<Map<String, Double>, Double>() {
			@Override
			public Double call(Map<String, Double> request) {
				double d = request.get("d");
				lock.set(d);
				return d;
			}
		});
		
		ScriptRunner.Engine subEngine = engine.sub();
		subEngine.eval("var echoed = syncEcho({'d':1.23});", null, new WaitLockScriptRunnerEnd(wait, lock));
		
		Assertions.assertThat(lock.waitFor()).isEqualTo(1.23d);
		wait.waitFor();
	}

	@Test
	public void testError() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		final Lock<String, Exception> lock = new Lock<>();
		
		ScriptRunner.Engine engine = runner.engine();
		engine.eval("err;", null, new ScriptRunner.End() {
			@Override
			public void failed(Exception e) {
				lock.set(e.getMessage());
			}
			@Override
			public void ended() {
				Assertions.fail("ended should not be called");
				lock.set(null);
			}
		});
		
		Assertions.assertThat(lock.waitFor().toString()).isNotNull();
	}

	@Test
	public void testSimpleSyncWithParameter() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<String, Exception> lock = new Lock<>();
		Wait wait = new Wait();
		
		engine.register("syncEcho", new SyncScriptFunction<Map<String, String>, String>() {
			@Override
			public String call(Map<String, String> request) {
				String c = request.get("c");
				String d = request.get("d");
				String r = c + "/" + d;
				lock.set(r);
				return r;
			}
		});
		
		ScriptRunner.Engine subEngine = engine.sub();
		subEngine.eval("var echoed = syncEcho({'c':a, 'd':('' + (b+1))});", ImmutableMap.of("a", "aa", "b", 1.23d), new WaitLockScriptRunnerEnd(wait, lock));
		
		Assertions.assertThat(lock.waitFor()).isEqualTo("aa/2.23");
		wait.waitFor();
	}

	@Test
	public void testSimpleAsyncWithParameter() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<String, Exception> lock = new Lock<>();
		Wait wait = new Wait();
		
		engine.register("asyncEcho", new AsyncScriptFunction<Map<String, String>, String>() {
			@Override
			public void call(Map<String, String> request, Callback<String> callback) {
				String c = request.get("c");
				String d = request.get("d");
				String r = c + "/" + d;
				lock.set(r);
				callback.handle(r);
				callback.done();
			}
		});
		
		ScriptRunner.Engine subEngine = engine.sub();
		subEngine.eval("asyncEcho({'c':a, 'd':('' + (b+1))});", ImmutableMap.of("a", "aa", "b", 1.23d), new WaitLockScriptRunnerEnd(wait, lock));
		
		Assertions.assertThat(lock.waitFor()).isEqualTo("aa/2.23");
		wait.waitFor();
	}

	@Test
	public void testContextIsolated() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<String, Exception> lock = new Lock<>();
		
		engine.register("syncEcho", new SyncScriptFunction<String, String>() {
			@Override
			public String call(String request) {
				lock.set(request);
				return request;
			}
		});

		{
			Wait wait = new Wait();
			engine.eval("var a = 'aa';", null, new WaitLockScriptRunnerEnd(wait, lock));
			wait.waitFor();
		}
		{
			Wait wait = new Wait();
			engine.eval("syncEcho(a);", null, new WaitLockScriptRunnerEnd(wait, lock));
			wait.waitFor();
		}
		
		try {
			lock.waitFor();
		} catch (Exception e) {
			Assertions.assertThat(e.getMessage()).contains("\"a\" is not defined");
		}
	}

	@Test
	public void testContextPassing() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<String, Exception> lock = new Lock<>();
		
		engine.register("syncEcho", new SyncScriptFunction<String, String>() {
			@Override
			public String call(String request) {
				lock.set(request);
				return request;
			}
		});

		{
			Wait wait = new Wait();
			engine.eval("$.a = 'aa';", null, new WaitLockScriptRunnerEnd(wait, lock));
			wait.waitFor();
		}
		{
			Wait wait = new Wait();
			engine.eval("syncEcho($.a);", null, new WaitLockScriptRunnerEnd(wait, lock));
			wait.waitFor();
		}
		
		Assertions.assertThat(lock.waitFor()).isEqualTo("aa");
	}
	
	@Test
	public void testPojoSync() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<String, Exception> lock = new Lock<>();
		
		engine.register("syncEcho1", new SyncScriptFunction<String, Map<String, String>>() {
			@Override
			public Map<String, String> call(String request) {
				Map<String, String> m = new HashMap<>();
				m.put("a", request);
				return m;
			}
		});
		engine.register("syncEcho2", new SyncScriptFunction<String, String>() {
			@Override
			public String call(String request) {
				lock.set(request);
				return request;
			}
		});

		Wait wait = new Wait();
		engine.eval("syncEcho2(syncEcho1('aa')['a']);", null, new WaitLockScriptRunnerEnd(wait, lock));
		wait.waitFor();
		
		Assertions.assertThat(lock.waitFor()).isEqualTo("aa");
	}

	@Test
	public void testPojoAsync() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<String, Exception> lock = new Lock<>();
		
		engine.register("asyncEcho1", new AsyncScriptFunction<String, Map<String, String>>() {
			@Override
			public void call(String request, Callback<Map<String, String>> callback) {
				Map<String, String> m = new HashMap<>();
				m.put("a", request);
				callback.handle(m).done();
			}
		});
		engine.register("syncEcho2", new SyncScriptFunction<String, String>() {
			@Override
			public String call(String request) {
				lock.set(request);
				return request;
			}
		});

		Wait wait = new Wait();
		engine.eval("asyncEcho1('aa', function(r) { syncEcho2(r['a']); });", null, new WaitLockScriptRunnerEnd(wait, lock));
		wait.waitFor();
		
		Assertions.assertThat(lock.waitFor()).isEqualTo("aa");
	}

	@Test
	public void testEachObject() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<String, Exception> lock = new Lock<>();
		
		engine.register("asyncEcho1", new AsyncScriptFunction<String, Map<String, String>>() {
			@Override
			public void call(String request, Callback<Map<String, String>> callback) {
				Map<String, String> m = new HashMap<>();
				m.put("a", request);
				callback.handle(m).done();
			}
		});
		engine.register("syncEcho2", new SyncScriptFunction<String, String>() {
			@Override
			public String call(String request) {
				lock.set(request);
				return request;
			}
		});

		Wait wait = new Wait();
		engine.eval("var each = function(o, callback) {"
				+ "if (o instanceof Array) {"
				+ "var f = function(i, callback) {"
					+ "if (i < o.length) {"
						+ "callback(o[i], i);"
						+ "f(i + 1, callback);"
					+ "}"
				+ "};"
				+ "f(0, callback);"
			+ "} else {"
				+ "var k;"
				+ "for (k in o) {"
					+ "callback(o[k], k);"
				+ "}"
			+ "}"
			+ "};"
			+ "asyncEcho1('aa', function(r) { each(r, function(v, k) { syncEcho2(v); }); });", null, new WaitLockScriptRunnerEnd(wait, lock));
		wait.waitFor();
		
		Assertions.assertThat(lock.waitFor()).isEqualTo("aa");
	}


	@Test
	public void testEachArray() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<String, Exception> lock = new Lock<>();
		
		engine.register("asyncEcho1", new AsyncScriptFunction<String, Map<String, String>>() {
			@Override
			public void call(String request, Callback<Map<String, String>> callback) {
				Map<String, String> m = new HashMap<>();
				m.put("a", request);
				callback.handle(m).done();
			}
		});
		engine.register("syncEcho2", new SyncScriptFunction<String, String>() {
			@Override
			public String call(String request) {
				lock.set(request);
				return request;
			}
		});

		Wait wait = new Wait();
		engine.eval("var each = function(o, callback) {"
				+ "if (o instanceof Array) {"
				+ "var f = function(i, callback) {"
					+ "if (i < o.length) {"
						+ "callback(o[i], i);"
						+ "f(i + 1, callback);"
					+ "}"
				+ "};"
				+ "f(0, callback);"
			+ "} else {"
				+ "var k;"
				+ "for (k in o) {"
					+ "callback(o[k], k);"
				+ "}"
			+ "}"
			+ "};"
			+ "asyncEcho1('aa', function(r) { each(r, function(v, k) { syncEcho2(v); }); });", null, new WaitLockScriptRunnerEnd(wait, lock));
		wait.waitFor();
		
		Assertions.assertThat(lock.waitFor()).isEqualTo("aa");
	}
//	array
}
