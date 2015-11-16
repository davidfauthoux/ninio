package com.davfx.ninio.script;

public final class ReadmeWithTelnet {
	public static void main(String[] args) throws Exception {
		try (ExtendedScriptRunner runner = new ExtendedScriptRunner()) {
			runner.runner.engine().eval("telnet({host:'127.0.0.1', init:[{'prompt':'login: '},{'command':'davidfauthoux','prompt':'Password:'},{'command':'orod,ove','prompt':'davidfauthoux$ '}], 'command': 'ls ~/Downloads', 'prompt': 'davidfauthoux$ '}, function(r) { console.log(r); });", null);
			Thread.sleep(10000);
		}
	}
}
