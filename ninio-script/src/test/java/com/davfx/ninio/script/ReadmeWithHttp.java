package com.davfx.ninio.script;

public final class ReadmeWithHttp {
	public static void main(String[] args) throws Exception {
		try (ExtendedScriptRunner runner = new ExtendedScriptRunner()) {
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
