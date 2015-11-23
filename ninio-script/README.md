ninio-script
============

```java
new ExtendedScriptRunner().runner.engine().eval("ping("
		+ "{"
			+ "'host': '127.0.0.1',"
		+ "}, function(r) {"
				+ "console.debug(JSON.stringify(r));"
			+ "}"
	+ ");", null);
```

```java
new ExtendedScriptRunner().runner.engine().eval("snmp("
		+ "{"
			+ "'host': '127.0.0.1',"
			+ "'oid': '1.3.6.1.2.1.1.4.0',"
			+ "'community': 'public'"
		+ "}, function(r) {"
				+ "console.debug(JSON.stringify(r));"
			+ "}"
	+ ");", null);
```

```java
String login = "<your-login>";
String password = "<your-password>";
new ExtendedScriptRunner().runner.engine().eval("telnet("
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
```

```java
String login = "<your-login>";
String password = "<your-password>";
new ExtendedScriptRunner().runner.engine().eval("ssh("
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
}
```

```java
new ExtendedScriptRunner().runner.engine().eval("http("
		+ "{"
			+ "'url': 'http://www.google.fr',"
		+ "}, function(r) {"
				+ "console.debug(JSON.stringify(r));"
			+ "}"
	+ ");", null);
```

