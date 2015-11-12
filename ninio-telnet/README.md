ninio-telnet
============

```java
final String login = "<your-login>";
final String password = "<your-password>";

new Telnet().to(new Address("127.0.0.1", Telnet.DEFAULT_PORT)).create().connect(new ReadyConnection() {
	private final StringBuilder received = new StringBuilder();
	private FailableCloseableByteBufferHandler write;
	private boolean done = false;
	@Override
	public void failed(IOException e) {
		e.printStackTrace();
	}
	@Override
	public void close() {
		System.out.println("Closed");
	}
	private void send(String line) {
		write.handle(null, ByteBuffer.wrap((line + TelnetClient.EOL).getBytes(TelnetClient.CHARSET)));
	}
	@Override
	public void handle(Address address, ByteBuffer buffer) {
		String s = new String(buffer.array(), buffer.position(), buffer.remaining());
		received.append(s);
		System.out.print(s);
		if (received.toString().endsWith("login: ")) {
			received.setLength(0);
			send(login);
		}
		if (received.toString().endsWith("Password:")) {
			received.setLength(0);
			send(password);
		}
		if (!done && received.toString().endsWith(login + "$ ")) {
			received.setLength(0);
			send("echo TEST");
			done = true;
		}
	}
	@Override
	public void connected(FailableCloseableByteBufferHandler write) {
		this.write = write;
	}
});
```

```java
final String login = "<your-login>";
final String password = "<your-password>";

List<CutOnPromptClient.NextCommand> commands = new LinkedList<>();
commands.add(new CutOnPromptClient.NextCommand("login:", login));
commands.add(new CutOnPromptClient.NextCommand("Password:", password));
commands.add(new CutOnPromptClient.NextCommand(login + "$", "ls"));
final Iterator<CutOnPromptClient.NextCommand> commandsIterator = commands.iterator();
new CutOnPromptClient(new Telnet().to(new Address("127.0.0.1", Telnet.DEFAULT_PORT)).create(), new CutOnPromptClient.Handler() {
	@Override
	public void failed(IOException e) {
		e.printStackTrace();
	}
	@Override
	public void close() {
		System.out.println("Closed");
	}
	@Override
	public NextCommand handle(String result) {
		System.out.println(result);
		if (commandsIterator.hasNext()) {
			return commandsIterator.next();
		} else {
			return null;
		}
	}
});
```
