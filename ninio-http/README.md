ninio-http
==========

```java
@Path("/ech")
public static final class EchoController implements HttpController {
	@Route(method = HttpMethod.GET, path = "/o")
	public Http echo(@QueryParameter("message") String message) {
		return Http.ok().content("ECHO " + message);
	}
}

AnnotatedHttpService server = new AnnotatedHttpService(new Queue(), new Address(Address.ANY, 8080));
server.register(EchoController.class);
System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/ech/o?message=helloworld");
```
