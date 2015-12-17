package com.davfx.ninio.script;

import com.davfx.ninio.core.Queue;

public final class ReadmeWithTelnet {
	public static void main(String[] args) throws Exception {
		String login = "<your-login>";
		String password = "<your-password>";
		
		try (Queue queue = new Queue()) {
			try (ExtendedScriptRunner runner = new ExtendedScriptRunner(queue, null, null, null)) {
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
}
