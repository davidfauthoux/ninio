package com.davfx.ninio.script;

import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.util.Lock;

public class TestMap {

	private static final Logger LOGGER = LoggerFactory.getLogger(TestMap.class);
	
	@Test
	public void testNashorn() throws Exception {
		try (ScriptRunner runner = new ExecutorScriptRunner()) {
			ScriptRunner.Engine engine = runner.engine();
			final Lock<Object, Exception> lock = new Lock<>();
			
			engine.register("trace", new SyncScriptFunction<Object, Object>() {
				@Override
				public Object call(Object request) {
					lock.set(request);
					return request;
				}
			});
			engine.register("syncEcho", new SyncScriptFunction<Map<String, String>, Map<String, String>>() {
				@Override
				public Map<String, String> call(Map<String,String> request) {
					Map<String, String> m = new HashMap<>();
					m.put("out", "cc");
					return m;
				}
			});
			
			engine.eval("var e = syncEcho({}); trace(e.out);", new ScriptRunner.End() {
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
	public void testRhino() throws Exception {
		try (ScriptRunner runner = new ExecutorScriptRunner("rhino")) {
			ScriptRunner.Engine engine = runner.engine();
			final Lock<Object, Exception> lock = new Lock<>();
			
			engine.register("trace", new SyncScriptFunction<Object, Object>() {
				@Override
				public Object call(Object request) {
					lock.set(request);
					return request;
				}
			});
			engine.register("syncEcho", new SyncScriptFunction<Map<String, String>, Map<String, String>>() {
				@Override
				public Map<String, String> call(Map<String,String> request) {
					Map<String, String> m = new HashMap<>();
					m.put("out", "cc");
					return m;
				}
			});
			
			engine.eval("var e = syncEcho({});"
					+ "function convert(o) {"
					+ "if (o instanceof java.util.Map) {"
					+ "var r = {};"
					+ "var i = o.keySet().iterator();"
					+ "while (i.hasNext()) {"
					+ "var k = i.next();"
					+ "r[k] = convert(o.get(k));"
					+ "}"
					+ "return r;"
					+ "} else {"
					+ "return o;"
					+ "}"
					+ "}"
					+ "e = convert(e);"
					+ " trace(e.out);", new ScriptRunner.End() {
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

}
