package com.davfx.ninio.script;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;

public class ScriptTest {

	@Test
	public void testVerySimpleSync() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<ScriptElement, Exception> lock = new Lock<>();
		Wait wait = new Wait();
		
		engine.register("syncEcho", new SyncScriptFunction() {
			@Override
			public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
				lock.set(request);
				return request;
			}
		});
		
		ScriptRunner.Engine subEngine = engine.sub();
		subEngine.eval("syncEcho('aaa');", new WaitLockScriptRunnerEnd(wait, lock));
		
		Assertions.assertThat(lock.waitFor().asString().value()).isEqualTo("aaa");
		wait.waitFor();
	}

	@Test
	public void testNullSimpleSync() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		
		{
			final Lock<ScriptElement, Exception> lock = new Lock<>();
			
			engine.register("syncEcho0", new SyncScriptFunction() {
				@Override
				public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
					lock.set(null);
					return request;
				}
			});
			engine.register("syncEcho1", new SyncScriptFunction() {
				@Override
				public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
					lock.set(request);
					return request;
				}
			});

			Wait wait = new Wait();
			ScriptRunner.Engine subEngine = engine.sub();
			subEngine.eval("syncEcho0('aaa');", new WaitLockScriptRunnerEnd(wait, lock));
			
			Assertions.assertThat(lock.waitFor()).isNull();
			wait.waitFor();
		}
		{
			final Lock<ScriptElement, Exception> lock = new Lock<>();
			
			engine.register("syncEcho0", new SyncScriptFunction() {
				@Override
				public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
					lock.set(null);
					return request;
				}
			});
			engine.register("syncEcho1", new SyncScriptFunction() {
				@Override
				public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
					lock.set(request);
					return request;
				}
			});

			Wait wait = new Wait();
			ScriptRunner.Engine subEngine = engine.sub();
			subEngine.eval("syncEcho1('aaa');", new WaitLockScriptRunnerEnd(wait, lock));
			
			Assertions.assertThat(lock.waitFor()).isNotNull();
			wait.waitFor();
		}
		{
			final Lock<ScriptElement, Exception> lock = new Lock<>();
			
			engine.register("syncEcho0", new SyncScriptFunction() {
				@Override
				public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
					lock.set(null);
					return request;
				}
			});
			engine.register("syncEcho1", new SyncScriptFunction() {
				@Override
				public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
					lock.set(request);
					return request;
				}
			});

			Wait wait = new Wait();
			ScriptRunner.Engine subEngine = engine.sub();
			subEngine.eval("syncEcho1(null);", new WaitLockScriptRunnerEnd(wait, lock));
			
			Assertions.assertThat(lock.waitFor()).isNull();
			wait.waitFor();
		}
		{
			final Lock<ScriptElement, Exception> lock = new Lock<>();
			
			engine.register("syncEcho0", new SyncScriptFunction() {
				@Override
				public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
					lock.set(null);
					return request;
				}
			});
			engine.register("syncEcho1", new SyncScriptFunction() {
				@Override
				public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
					lock.set(request);
					return request;
				}
			});

			Wait wait = new Wait();
			ScriptRunner.Engine subEngine = engine.sub();
			subEngine.eval("syncEcho1(undefined);", new WaitLockScriptRunnerEnd(wait, lock));
			
			Assertions.assertThat(lock.waitFor()).isNull();
			wait.waitFor();
		}
		{
			final Lock<ScriptElement, Exception> lock = new Lock<>();
			
			engine.register("syncEcho0", new SyncScriptFunction() {
				@Override
				public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
					lock.set(null);
					return request;
				}
			});
			engine.register("syncEcho1", new SyncScriptFunction() {
				@Override
				public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
					lock.set(request);
					return request;
				}
			});

			Wait wait = new Wait();
			ScriptRunner.Engine subEngine = engine.sub();
			subEngine.eval("syncEcho1('');", new WaitLockScriptRunnerEnd(wait, lock));
			
			Assertions.assertThat(lock.waitFor()).isNotNull();
			wait.waitFor();
		}
	}

	@Test
	public void testSimpleSync() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<ScriptElement, Exception> lock = new Lock<>();
		Wait wait = new Wait();
		
		engine.register("syncEcho1", new SyncScriptFunction() {
			@Override
			public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
				ScriptObjectBuilder m = builder.object();
				m.put("out", request.asObject().get("message"));
				return m.build();
			}
		});
		
		engine.register("syncEcho2", new SyncScriptFunction() {
			@Override
			public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
				lock.set(request.asObject().get("out"));
				return request;
			}
		});
		
		ScriptRunner.Engine subEngine = engine.sub();
		subEngine.eval("var echoed = syncEcho2(syncEcho1({'message':'bb'}));", new WaitLockScriptRunnerEnd(wait, lock));
		
		Assertions.assertThat(lock.waitFor().asString().value()).isEqualTo("bb");
		wait.waitFor();
	}

	@Test
	public void testArraySync() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<ScriptElement, Exception> lock = new Lock<>();
		Wait wait = new Wait();
		
		engine.register("syncEcho1", new SyncScriptFunction() {
			@Override
			public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
				ScriptArray a = request.asArray();
				ScriptArrayBuilder r = builder.array();
				for (ScriptElement e : a) {
					r.add(e);
				}
				r.add(builder.string("+"));
				return r.build();
			}
		});
		
		engine.register("syncEcho2", new SyncScriptFunction() {
			@Override
			public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
				lock.set(request);
				return request;
			}
		});
		
		ScriptRunner.Engine subEngine = engine.sub();
		subEngine.eval("syncEcho2(syncEcho1(['aa', 'bb'])[2]);", new WaitLockScriptRunnerEnd(wait, lock));
		Assertions.assertThat(lock.waitFor().asString().value()).isEqualTo("+");
		wait.waitFor();
	}

	@Test
	public void testSimpleAsync() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<ScriptElement, Exception> lock = new Lock<>();
		Wait wait = new Wait();

		engine.register("syncEcho", new SyncScriptFunction() {
			@Override
			public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
				lock.set(request);
				return request;
			}
		});
		engine.register("asyncEcho", new AsyncScriptFunction() {
			@Override
			public void call(ScriptElement request, ScriptElementBuilder builder, Callback callback) {
				callback.handle(request);
				callback.done();
			}
		});
		
		ScriptRunner.Engine subEngine = engine.sub();
		subEngine.eval("asyncEcho('aaa', syncEcho);", new WaitLockScriptRunnerEnd(wait, lock));
		
		Assertions.assertThat(lock.waitFor().asString().value()).isEqualTo("aaa");
		wait.waitFor();
	}

	@Test
	public void testSimpleSyncWithDouble() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<Double, Exception> lock = new Lock<>();
		Wait wait = new Wait();
		
		engine.register("syncEcho", new SyncScriptFunction() {
			@Override
			public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
				double d = request.asObject().get("d").asNumber().value();
				lock.set(d);
				return builder.number(d);
			}
		});
		
		ScriptRunner.Engine subEngine = engine.sub();
		subEngine.eval("var echoed = syncEcho({'d':1.23});", new WaitLockScriptRunnerEnd(wait, lock));
		
		Assertions.assertThat(lock.waitFor()).isEqualTo(1.23d);
		wait.waitFor();
	}

	@Test
	public void testError() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		final Lock<String, Exception> lock = new Lock<>();
		
		ScriptRunner.Engine engine = runner.engine();
		engine.eval("err;", new ScriptRunner.End() {
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
		
		engine.register("syncEcho", new SyncScriptFunction() {
			@Override
			public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
				String c = request.asObject().get("c").asString().value();
				String d = request.asObject().get("d").asString().value();
				String r = c + "/" + d;
				lock.set(r);
				return builder.string(r);
			}
		});
		
		ScriptRunner.Engine subEngine = engine.sub();
		subEngine.register("a", new ScriptParameterString("aa"));
		subEngine.register("b", new ScriptParameter() {
			@Override
			public ScriptElement build(ScriptElementBuilder builder) {
				return builder.object().put("b", builder.number(1.23d)).build();
			}
		});
		subEngine.eval("var echoed = syncEcho({'c':a, 'd':('' + (b.b+1))});", new WaitLockScriptRunnerEnd(wait, lock));
		
		Assertions.assertThat(lock.waitFor()).isEqualTo("aa/2.23");
		wait.waitFor();
	}

	@Test
	public void testSimpleAsyncWithParameter() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<String, Exception> lock = new Lock<>();
		Wait wait = new Wait();
		
		engine.register("asyncEcho", new AsyncScriptFunction() {
			@Override
			public void call(ScriptElement request, ScriptElementBuilder builder, Callback callback) {
				String c = request.asObject().get("c").asString().value();
				String d = request.asObject().get("d").asString().value();
				String r = c + "/" + d;
				lock.set(r);
				callback.handle(builder.string(r));
				callback.done();
			}
		});
		
		ScriptRunner.Engine subEngine = engine.sub();
		subEngine.register("a", new ScriptParameterString("aa"));
		subEngine.register("b", new ScriptParameter() {
			@Override
			public ScriptElement build(ScriptElementBuilder builder) {
				return builder.object().put("b", builder.number(1.23d)).build();
			}
		});
		subEngine.eval("java.lang.System.out.println((typeof b) + ' b='+b);java.lang.System.out.println('b.b='+b.b);asyncEcho({'c':a, 'd':('' + (b['b']+1))});", new WaitLockScriptRunnerEnd(wait, lock));
		
		Assertions.assertThat(lock.waitFor()).isEqualTo("aa/2.23");
		wait.waitFor();
	}

	@Test
	public void testContextIsolated() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<ScriptElement, Exception> lock = new Lock<>();
		
		engine.register("syncEcho", new SyncScriptFunction() {
			@Override
			public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
				lock.set(request);
				return request;
			}
		});

		{
			Wait wait = new Wait();
			engine.eval("var a = 'aa';", new WaitLockScriptRunnerEnd(wait, lock));
			wait.waitFor();
		}
		{
			Wait wait = new Wait();
			engine.eval("syncEcho(a);", new WaitLockScriptRunnerEnd(wait, lock));
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
		final Lock<ScriptElement, Exception> lock = new Lock<>();
		
		engine.register("syncEcho", new SyncScriptFunction() {
			@Override
			public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
				lock.set(request);
				return request;
			}
		});

		{
			Wait wait = new Wait();
			engine.eval("$.a = 'aa';", new WaitLockScriptRunnerEnd(wait, lock));
			wait.waitFor();
		}
		{
			Wait wait = new Wait();
			engine.eval("syncEcho($.a);", new WaitLockScriptRunnerEnd(wait, lock));
			wait.waitFor();
		}
		
		Assertions.assertThat(lock.waitFor().asString().value()).isEqualTo("aa");
	}
	
	@Test
	public void testPojoSync() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<ScriptElement, Exception> lock = new Lock<>();
		
		engine.register("syncEcho1", new SyncScriptFunction() {
			@Override
			public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
				return builder.object().put("a", request).build();
			}
		});
		engine.register("syncEcho2", new SyncScriptFunction() {
			@Override
			public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
				lock.set(request);
				return request;
			}
		});

		Wait wait = new Wait();
		engine.eval("syncEcho2(syncEcho1('aa')['a']);", new WaitLockScriptRunnerEnd(wait, lock));
		wait.waitFor();
		
		Assertions.assertThat(lock.waitFor().asString().value()).isEqualTo("aa");
	}

	@Test
	public void testPojoAsync() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<ScriptElement, Exception> lock = new Lock<>();
		
		engine.register("asyncEcho1", new AsyncScriptFunction() {
			@Override
			public void call(ScriptElement request, ScriptElementBuilder builder, Callback callback) {
				callback.handle(builder.object().put("a", request).build()).done();
			}
		});
		engine.register("syncEcho2", new SyncScriptFunction() {
			@Override
			public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
				lock.set(request);
				return request;
			}
		});

		Wait wait = new Wait();
		engine.eval("asyncEcho1('aa', function(r) { syncEcho2(r['a']); });", new WaitLockScriptRunnerEnd(wait, lock));
		wait.waitFor();
		
		Assertions.assertThat(lock.waitFor().asString().value()).isEqualTo("aa");
	}

	@Test
	public void testEachObject() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<ScriptElement, Exception> lock = new Lock<>();
		
		engine.register("asyncEcho1", new AsyncScriptFunction() {
			@Override
			public void call(ScriptElement request, ScriptElementBuilder builder, Callback callback) {
				callback.handle(builder.object().put("a", request).build()).done();
			}
		});
		engine.register("syncEcho2", new SyncScriptFunction() {
			@Override
			public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
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
			+ "asyncEcho1('aa', function(r) { each(r, function(v, k) { syncEcho2(v); }); });", new WaitLockScriptRunnerEnd(wait, lock));
		wait.waitFor();
		
		Assertions.assertThat(lock.waitFor().asString().value()).isEqualTo("aa");
	}


	@Test
	public void testEachArray() throws Exception {
		ScriptRunner runner = new ExecutorScriptRunner();
		ScriptRunner.Engine engine = runner.engine();
		final Lock<ScriptElement, Exception> lock = new Lock<>();
		
		engine.register("asyncEcho1", new AsyncScriptFunction() {
			@Override
			public void call(ScriptElement request, ScriptElementBuilder builder, Callback callback) {
				callback.handle(builder.object().put("a", request).build()).done();
			}
		});
		engine.register("syncEcho2", new SyncScriptFunction() {
			@Override
			public ScriptElement call(ScriptElement request, ScriptElementBuilder builder) {
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
			+ "asyncEcho1('aa', function(r) { each(r, function(v, k) { syncEcho2(v); }); });", new WaitLockScriptRunnerEnd(wait, lock));
		wait.waitFor();
		
		Assertions.assertThat(lock.waitFor().asString().value()).isEqualTo("aa");
	}
//	array
}
