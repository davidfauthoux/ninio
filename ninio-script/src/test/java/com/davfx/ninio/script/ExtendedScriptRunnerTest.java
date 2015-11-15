package com.davfx.ninio.script;

import org.junit.Test;

public class ExtendedScriptRunnerTest {

	@Test
	public void test() throws Exception {
		try (ExtendedScriptRunner runner = new ExtendedScriptRunner()) {
			runner.runner.engine().eval("http({url:'http://david.fauthoux.free.fr/iii.html'}, function(r) { console.log(r); });", null);
			Thread.sleep(10000);
		}
	}

}
