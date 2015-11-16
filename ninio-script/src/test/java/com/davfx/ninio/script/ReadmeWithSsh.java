package com.davfx.ninio.script;

///!\ NOT WORKING WITH Java7 ON UP-TO-DATE open-ssl SERVERS

public final class ReadmeWithSsh {
	public static void main(String[] args) throws Exception {
		try (ExtendedScriptRunner runner = new ExtendedScriptRunner()) {
			runner.runner.engine().eval("ssh({host:'127.0.0.1', 'login': 'davidfauthoux', 'password': 'orod,ove', 'init': [{'prompt': ''}], 'command': 'ls', 'prompt': 'davidfauthoux$ '}, function(r) { console.log(r); });", null);
			Thread.sleep(100000);
		}
	}
}
