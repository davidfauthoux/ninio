package com.davfx.ninio.script;

import com.davfx.ninio.core.Queue;

///!\ NOT WORKING WITH Java7 ON UP-TO-DATE open-ssl SERVERS

public final class ReadmeWithSsh {
	public static void main(String[] args) throws Exception {
		String login = "<your-login>";
		String password = "<your-password>";
		
		try (Queue queue = new Queue()) {
			try (ExtendedScriptRunner runner = new ExtendedScriptRunner(queue, null, null, null)) {
				runner.runner.engine().eval("ssh("
						+ "{"
							+ "'host': '127.0.0.1',"
							+ "'login': '" + login + "',"
							+ "'password': '" + password + "',"
							+ "'init': ["
										+ "{"
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
