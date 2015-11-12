ninio-ssh
=========

```java
new Ssh()
	.withLogin("<your-login>")
	.withPassword("<your-password>")
.to(new Address("127.0.0.1", Ssh.DEFAULT_PORT)).create().connect(new ReadyConnection() {
	private FailableCloseableByteBufferHandler write;
	@Override
	public void failed(IOException e) {
		e.printStackTrace();
	}
	@Override
	public void close() {
		System.out.println("Closed");
	}
	private void send(String line) {
		write.handle(null, ByteBuffer.wrap((line + '\n').getBytes(Charsets.UTF_8)));
	}
	@Override
	public void handle(Address address, ByteBuffer buffer) {
		String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
		System.out.print(s);
	}
	@Override
	public void connected(FailableCloseableByteBufferHandler write) {
		this.write = write;
		send("echo TEST");
	}
});
```

```java
Trust trust = new Trust("/keystore.jks", "test-password", "/keystore.jks", "test-password");

new Ssh()
	.withLogin("<your-login>")
	.withKey(trust, "test-alias", "test-password")
.to(new Address("127.0.0.1", Ssh.DEFAULT_PORT)).create().connect(new ReadyConnection() {
	private FailableCloseableByteBufferHandler write;
	@Override
	public void failed(IOException e) {
		e.printStackTrace();
	}
	@Override
	public void close() {
		System.out.println("Closed");
	}
	private void send(String line) {
		write.handle(null, ByteBuffer.wrap((line + '\n').getBytes(Charsets.UTF_8)));
	}
	@Override
	public void handle(Address address, ByteBuffer buffer) {
		String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
		System.out.print(s);
	}
	@Override
	public void connected(FailableCloseableByteBufferHandler write) {
		this.write = write;
		send("echo TEST");
	}
});
```

```
new Ssh()
	.withLogin("<your-login>")
	.withPassword("<your-password>")
.to(new Address("127.0.0.1", Ssh.DEFAULT_PORT)).download("todownload.txt", new ToFileFailableCloseableByteBufferHandler(new File("downloaded.txt"), new Failable() {
	@Override
	public void failed(IOException e) {
		if (e == null) {
			System.out.println("Done");
			return;
		}
		e.printStackTrace();
	}
}));
```
		