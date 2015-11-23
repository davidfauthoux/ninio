package com.davfx.ninio.script;

public final class ReadmeWithPing {
	public static void main(String[] args) throws Exception {
		try (ExtendedScriptRunner runner = new ExtendedScriptRunner()) {
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
