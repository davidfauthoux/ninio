package com.davfx.ninio.script;

import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.util.Lock;

public class ScriptTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(ScriptTest.class);
	
	@Test
	public void testSimpleSync() throws Exception {
		try (ScriptRunner runner = new ExecutorScriptRunner()) {
			ScriptRunner.Engine engine = runner.engine();
			final Lock<Object, Exception> lock = new Lock<>();
			
			engine.register("syncEcho1", new SyncScriptFunction<Map<String, String>, Map<String, String>>() {
				@Override
				public Map<String, String> call(Map<String,String> request) {
					LOGGER.debug("1 -------> {}", request);
					Map<String, String> m = new HashMap<>();
					m.put("out", "cc");
					return m;
				}
			});
			
			engine.register("syncEcho2", new SyncScriptFunction<Map<String, String>, Map<String, String>>() {
				@Override
				public Map<String, String> call(Map<String,String> request) {
					LOGGER.debug("2 -------> {}", request);
					lock.set(request.get("out"));
					return request;
				}
			});
			
			ScriptRunner.Engine subEngine = engine.sub();
			subEngine.eval("var echoed = syncEcho2(syncEcho1({'message':'bb'}));", new ScriptRunner.End() {
				@Override
				public void failed(Exception e) {
					lock.fail(e);
				}
				@Override
				public void ended() {
					LOGGER.debug("End");
				}
			});
			
			Assertions.assertThat(lock.waitFor().toString()).isEqualTo("cc");
		}
	}

	@Test
	public void testSimpleAsync() throws Exception {
		try (ScriptRunner runner = new ExecutorScriptRunner()) {
			ScriptRunner.Engine engine = runner.engine();
			final Lock<Object, Exception> lock = new Lock<>();
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
				}
			});
			
			ScriptRunner.Engine subEngine = engine.sub();
			subEngine.eval("asyncEcho('aaa', function(r) { syncEcho(r); });", new ScriptRunner.End() {
				@Override
				public void failed(Exception e) {
					lock.fail(e);
				}
				@Override
				public void ended() {
					LOGGER.debug("End");
				}
			});
			
			Assertions.assertThat(lock.waitFor()).isEqualTo("aaa");
		}
	}

	@Test
	public void testSimpleSyncWithDouble() throws Exception {
		try (ScriptRunner runner = new ExecutorScriptRunner()) {
			ScriptRunner.Engine engine = runner.engine();
			final Lock<Double, Exception> lock = new Lock<>();
			
			engine.register("syncEcho", new SyncScriptFunction<Map<String, Double>, Double>() {
				@Override
				public Double call(Map<String, Double> request) {
					double d = request.get("d");
					lock.set(d);
					return d;
				}
			});
			
			ScriptRunner.Engine subEngine = engine.sub();
			subEngine.eval("var echoed = syncEcho({'d':1.23});", new ScriptRunner.End() {
				@Override
				public void failed(Exception e) {
					lock.fail(e);
				}
				@Override
				public void ended() {
					LOGGER.debug("End");
				}
			});
			
			Assertions.assertThat(lock.waitFor()).isEqualTo(1.23d);
		}
	}

	@Test
	public void testBindingsPassingFromContextToSyncContext() throws Exception {
		try (ScriptRunner runner = new ExecutorScriptRunner()) {
			{
				final Lock<Double, Exception> lock = new Lock<>();
				ScriptRunner.Engine engine = runner.engine();
				engine.register("syncEcho", new SyncScriptFunction<Map<String, Double>, Double>() {
					@Override
					public Double call(Map<String, Double> request) {
						double d = request.get("d");
						lock.set(d);
						return d;
					}
				});
				
				engine.eval("glob = {'d':1.23};", new ScriptRunner.End() { //TODO var ???
					@Override
					public void failed(Exception e) {
						lock.fail(e);
					}
					@Override
					public void ended() {
						LOGGER.debug("End");
					}
				});
				
				engine.eval("var echoed = syncEcho(glob);", new ScriptRunner.End() {
					@Override
					public void failed(Exception e) {
						lock.fail(e);
					}
					@Override
					public void ended() {
						LOGGER.debug("End");
					}
				});
				
				Assertions.assertThat(lock.waitFor()).isEqualTo(1.23d);
			}
			/*TODO Should fail on this second time
			{
				final Lock<Double, Exception> lock = new Lock<>();
				ScriptRunner.Engine engine = runner.engine();
				engine.register("syncEcho", new SyncScriptFunction<Map<String, Double>, Double>() {
					@Override
					public Double call(Map<String, Double> request) {
						double d = request.get("d");
						lock.set(d);
						return d;
					}
				});
				
				engine.eval("var echoed = syncEcho(glob);", new ScriptRunner.End() {
					@Override
					public void failed(Exception e) {
						lock.fail(e);
					}
					@Override
					public void ended() {
						LOGGER.debug("End");
					}
				});
				
				Assertions.assertThat(lock.waitFor()).isEqualTo(1.23d);
			}
			*/
		}
	}

	@Test
	public void testBindingsPassingFromContextToAsyncContext() throws Exception {
		try (ScriptRunner runner = new ExecutorScriptRunner()) {
			final Lock<Object, Exception> lock = new Lock<>();
			
			ScriptRunner.Engine engine = runner.engine();
			engine.register("asyncEcho", new AsyncScriptFunction<Map<String, String>, String>() {
				@Override
				public void call(Map<String, String> request, AsyncScriptFunction.Callback<String> callback) {
					callback.handle(request.get("param"));
				}
			});
			
			engine.register("out", new SyncScriptFunction<Object, Void>() {
				@Override
				public Void call(Object request) {
					lock.set(request);
					return null;
				}
			});
			engine.eval("glob = 'ggg';", new ScriptRunner.End() { //TODO var ???
				@Override
				public void failed(Exception e) {
					lock.fail(e);
				}
				@Override
				public void ended() {
					LOGGER.debug("End");
				}
			});
			
			engine.eval("var echoed = asyncEcho({'param':glob}, function(r) { asyncEcho({'param':glob}, function(r) { out(glob); }); });", new ScriptRunner.End() {
				@Override
				public void failed(Exception e) {
					lock.fail(e);
				}
				@Override
				public void ended() {
					LOGGER.debug("End");
				}
			});
			
			Assertions.assertThat(lock.waitFor()).isEqualTo("ggg");
		}
	}

	@Test
	public void testSync() throws Exception {
		try (ScriptRunner runner = new ExecutorScriptRunner()) {
			ScriptRunner.Engine engine = runner.engine();
			final Lock<Object, Exception> lock = new Lock<>();
			
			engine.register("syncEcho", new SyncScriptFunction<Map<String, String>, Map<String, String>>() {
				@Override
				public Map<String, String> call(Map<String, String> request) {
					Map<String, String> o = new HashMap<>();
					o.put("message", "synchEcho " + request.get("message"));
					return o;
				}
			});
			engine.eval("var echoed = syncEcho({'message':'aa'});", new ScriptRunner.End() {
				@Override
				public void failed(Exception e) {
					lock.fail(e);
				}
				@Override
				public void ended() {
					LOGGER.debug("syncEcho end");
				}
			});
			
			ScriptRunner.Engine subEngine = engine.sub();
			subEngine.register("syncEcho2", new SyncScriptFunction<Map<String, String>, Map<String, String>>() {
				@Override
				public Map<String, String> call(Map<String, String> request) {
					Map<String, String> o = new HashMap<>();
					o.put("message", "synchEcho2 " + request.get("message"));
					return o;
				}
			});
			subEngine.register("out", new SyncScriptFunction<Object, Void>() {
				@Override
				public Void call(Object request) {
					lock.set(request);
					return null;
				}
			});
			subEngine.eval("var echoed2 = syncEcho2({'message':'bb'});", new ScriptRunner.End() {
				@Override
				public void failed(Exception e) {
					lock.fail(e);
				}
				@Override
				public void ended() {
					LOGGER.debug("syncEcho2 end");
				}
			});
			subEngine.eval("out(syncEcho(syncEcho2({'message':'bb'})));", new ScriptRunner.End() {
				@Override
				public void failed(Exception e) {
					lock.fail(e);
				}
				@Override
				public void ended() {
					LOGGER.debug("eval end");
				}
			});
			
			Assertions.assertThat(lock.waitFor().toString()).isEqualTo("{message=synchEcho synchEcho2 bb}");
		}
	}

	@Test
	public void testAsync() throws Exception {
		try (ScriptRunner runner = new ExecutorScriptRunner()) {
			ScriptRunner.Engine engine = runner.engine();
			final Lock<Object, Exception> lock = new Lock<>();
			
			engine.register("asyncEcho", new AsyncScriptFunction<Map<String, String>, Map<String, String>>() {
				@Override
				public void call(Map<String, String> o, Callback<Map<String, String>> callback) {
					Map<String, String> m = new HashMap<>();
					m.put("message", "asynchEcho " + o.get("message"));
					callback.handle(m);
				}
			});
			engine.eval("asyncEcho({'message':'aa'}, function(r) { });", new ScriptRunner.End() {
				@Override
				public void failed(Exception e) {
					lock.fail(e);
				}
				@Override
				public void ended() {
					LOGGER.debug("asyncEcho end");
				}
			});
			
			ScriptRunner.Engine subEngine = engine.sub();
			subEngine.register("asyncEcho2", new AsyncScriptFunction<Map<String, String>, Map<String, String>>() {
				@Override
				public void call(Map<String, String> o, Callback<Map<String, String>> callback) {
					Map<String, String> m = new HashMap<>();
					m.put("message", "asynchEcho2 " + o.get("message"));
					callback.handle(m);
				}
			});
			subEngine.register("out", new SyncScriptFunction<Object, Void>() {
				@Override
				public Void call(Object request) {
					lock.set(request);
					return null;
				}
			});
			subEngine.eval("asyncEcho2({'message':'bb'}, function(r) { });", new ScriptRunner.End() {
				@Override
				public void failed(Exception e) {
					lock.fail(e);
				}
				@Override
				public void ended() {
					LOGGER.debug("asyncEcho2 end");
				}
			});
			subEngine.eval("asyncEcho2({'message':'bb'}, function(r) { asyncEcho(r, function(r2) { out(r2); }); });", new ScriptRunner.End() {
				@Override
				public void failed(Exception e) {
					lock.fail(e);
				}
				@Override
				public void ended() {
					LOGGER.debug("eval end");
				}
			});
			
			Assertions.assertThat(lock.waitFor().toString()).isEqualTo("{message=asynchEcho asynchEcho2 bb}");
		}
	}

	@Test
	public void testError() throws Exception {
		try (ScriptRunner runner = new ExecutorScriptRunner()) {
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
	}

}
