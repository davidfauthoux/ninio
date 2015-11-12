ninio-ping
==========

```java
new Ping().ping("127.0.0.1", new PingCallback() {
	@Override
	public void failed(IOException e) {
		e.printStackTrace();
	}
	@Override
	public void pong(double time) {
		System.out.println(time);
	}
});
```
