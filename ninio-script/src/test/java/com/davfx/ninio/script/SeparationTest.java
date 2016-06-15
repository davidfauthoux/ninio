package com.davfx.ninio.script;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.util.Lock;

public class SeparationTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(SeparationTest.class);
	
	@Test
	public void testSimpleSync() throws Exception {
		final Executor ee = Executors.newSingleThreadExecutor();
		try (ScriptRunner runner = new ExecutorScriptRunner()) {
			ScriptRunner.Engine engine = runner.engine();
			final Lock<Object, Exception> lock = new Lock<>();

			{
				ScriptRunner.Engine subEngine = engine.sub();
	
				subEngine.register("asyncEcho", new AsyncScriptFunction<String, String>() {
					@Override
					public void call(String request, AsyncScriptFunction.Callback<String> callback) {
						LOGGER.debug("1/ request={}", request);
						ee.execute(new Runnable() {
							@Override
							public void run() {
								try {
									Thread.sleep(100);
								} catch (InterruptedException e) {
								}
								callback.handle(request);
							}
						});
					}
				});
				
				subEngine.eval("var v = 'aa'; asyncEcho(v, function(r) { asyncEcho(v, function() { asyncEcho(v, function() {}); }); });", new ScriptRunner.End() {
					@Override
					public void failed(Exception e) {
						lock.fail(e);
					}
					@Override
					public void ended() {
						LOGGER.debug("End");
					}
				});
			}

			{
				ScriptRunner.Engine subEngine = engine.sub();
	
				subEngine.register("asyncEcho", new AsyncScriptFunction<String, String>() {
					@Override
					public void call(String request, AsyncScriptFunction.Callback<String> callback) {
						LOGGER.debug("2/ request={}", request);
						ee.execute(new Runnable() {
							@Override
							public void run() {
								try {
									Thread.sleep(100);
								} catch (InterruptedException e) {
								}
								callback.handle(request);
							}
						});
					}
				});
				
				subEngine.eval("var v = 'bb'; asyncEcho(v, function(r) { asyncEcho(v, function() { asyncEcho(v, function() {}); }); });", new ScriptRunner.End() {
					@Override
					public void failed(Exception e) {
						lock.fail(e);
					}
					@Override
					public void ended() {
						LOGGER.debug("End");
					}
				});
			}
			Assertions.assertThat(lock.waitFor().toString()).isEqualTo("{r=aa}");
		}
	}

}
