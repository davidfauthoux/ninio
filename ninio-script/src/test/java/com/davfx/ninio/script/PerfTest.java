package com.davfx.ninio.script;

import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.Lock;

public class PerfTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(PerfTest.class);
	
	private static void evalSync(String name, boolean newScriptEveryTime) throws Exception {
		long min = Long.MAX_VALUE;
		for (int k = 0; k < 3; k++) {
			ScriptRunner runner = new ExecutorScriptRunner(name);
			long n = 1_000L;
			long t = System.currentTimeMillis();
			for (long i = 0; i < n; i++) {
				final Lock<Void, Exception> lock = new Lock<>();
				
				ScriptRunner.Engine engine = runner.engine();
				engine.register("syncEcho", new SyncScriptFunction<Object, Object>() {
					@Override
					public Object call(Object request) {
						// LOGGER.debug("syncEcho({})", request);
						return request;
					}
				});
				engine.eval((newScriptEveryTime ? "var i" + i + " = 0;" : "") + " syncEcho({'message':'bb'});", null, new ScriptRunner.End() {
					@Override
					public void failed(Exception e) {
						lock.fail(e);
					}
					@Override
					public void ended() {
						// LOGGER.debug("end");
						lock.set(null);
					}
				});
				
				Assertions.assertThat(lock.waitFor()).isNull();
			}
			t = System.currentTimeMillis() - t;
			long tt = t * 1000L / n;
			min = Math.min(tt, min);
			LOGGER.debug("{}: {}", name, tt);
		}
		LOGGER.debug("{}: min {}", name, min);
	}
	
	@Ignore
	@Test
	public void testSyncNotNewScriptEveryTime() throws Exception {
		evalSync(null, false);
		evalSync("rhino", false);
		//evalSync("jav8", false);
	}

	@Ignore
	@Test
	public void testSyncNewScriptEveryTime() throws Exception {
		evalSync(null, true);
		evalSync("rhino", true);
		//evalSync("jav8", true);
	}

}
