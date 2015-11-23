package com.davfx.ninio.script;

public final class ReadmeWithTelnet {
	public static void main(String[] args) throws Exception {
		String login = "<your-login>";
		String password = "<your-password>";
		
		try (ExtendedScriptRunner runner = new ExtendedScriptRunner()) {
			runner.runner.engine().eval("telnet("
					+ "{"
						+ "'host': '127.0.0.1',"
						+ "'init': ["
									+ "{"
										+ "'prompt': 'login: '"
									+ "},"
									+ "{"
										+ "'command': '" + login + "',"
										+ "'prompt': 'Password:'"
									+ "},"
									+ "{"
										+ "'command': '" + password + "',"
										+ "'prompt': '" + login + "$ '"
									+ "}"
								+ "],"
						+ "'command': 'ls',"
						+ "'prompt': '" + login + "$ '"
					+ "}, function(r) {"
							+ "console.log(r);"
						+ "}"
				+ ");", null);
			
			Thread.sleep(10000);
		}
	}
}
