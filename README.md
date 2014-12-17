ninio
=====

A Java NIO HTTP client/server as light as possible.


The server:

```java
new SimpleHttpServer(new HttpServerConfigurator().withPort(8080)).start(new DefaultSimpleHttpServerHandler() {
	@Override
	public String get(String path, Parameters parameters) {
		return "ECHO GET " + path + " " + parameters;
	}
	@Override
	public String post(String path, Parameters parameters, InMemoryPost post) {
		return "ECHO POST " + path + " " + parameters + " " + post.toString();
	}
});
```

The client:

```java
new SimpleHttpClient(new HttpClientConfigurator().withHost("localhost").withPort(8080)).on("/path?foo=bar").send(new SimpleHttpClientHandler() {
	@Override
	public void handle(int status, String reason, InMemoryPost body) {
		System.out.println("[" + status + "] " + reason + " / " + body);
	}
});
```

Available on Maven Central:

```xml
<dependency>
	<groupId>com.davfx</groupId>
	<artifactId>ninio</artifactId>
	<version>0.0.2</version>
</dependency>
```

The code has been written by [David Fauthoux](davfx.com) and is the property of [Living Objects](livingobjects.com).