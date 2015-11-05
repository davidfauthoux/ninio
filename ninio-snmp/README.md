ninio-snmp
==========

```java
new Snmp().to(new Address("127.0.0.1", Snmp.DEFAULT_PORT)).get(new Oid("1.3.6.1.2.1.1.4.0"), new GetCallback() {
	@Override
	public void failed(IOException e) {
		e.printStackTrace();
	}
	@Override
	public void close() {
		System.out.println("Done");
	}
	@Override
	public void result(Result result) {
		System.out.println("Result: " + result);
	}
});
```
