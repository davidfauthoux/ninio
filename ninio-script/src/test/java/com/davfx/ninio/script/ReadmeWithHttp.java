package com.davfx.ninio.script;

import com.davfx.ninio.core.Queue;

public final class ReadmeWithHttp {
	public static void main(String[] args) throws Exception {
		try (Queue queue = new Queue()) {
			try (ExtendedScriptRunner runner = new ExtendedScriptRunner(queue, null, null, null)) {
				runner.runner.engine().eval("http("
						+ "{"
							+ "'url': 'http://www.google.fr',"
						+ "}, function(r) {"
								+ "console.debug(JSON.stringify(r));"
							+ "}"
					+ ");", null);
				
				Thread.sleep(10000);
			}
		}
	}
}
