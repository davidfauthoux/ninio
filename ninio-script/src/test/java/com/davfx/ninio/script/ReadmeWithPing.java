package com.davfx.ninio.script;

import com.davfx.ninio.core.DatagramReadyFactory;
import com.davfx.ninio.core.Queue;

public final class ReadmeWithPing {
	public static void main(String[] args) throws Exception {
		try (Queue queue = new Queue()) {
			try (ExtendedScriptRunner runner = new ExtendedScriptRunner(queue, new DatagramReadyFactory(queue))) {
				runner.runner.engine().eval("ping("
						+ "{"
							+ "'host': '127.0.0.1',"
						+ "}, function(r) {"
								+ "console.debug(JSON.stringify(r));"
							+ "}"
					+ ");", null);
				
				Thread.sleep(10000);
			}
		}
	}
}
