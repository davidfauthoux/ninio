package com.davfx.ninio.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Listener;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.http.service.Annotated;
import com.davfx.ninio.http.service.HttpContentType;
import com.davfx.ninio.http.service.HttpController;
import com.davfx.ninio.http.service.HttpPost;
import com.davfx.ninio.http.service.HttpService;
import com.davfx.ninio.http.service.HttpServiceRequest;
import com.davfx.ninio.http.service.annotations.BodyParameter;
import com.davfx.ninio.http.service.annotations.DefaultValue;
import com.davfx.ninio.http.service.annotations.HeaderParameter;
import com.davfx.ninio.http.service.annotations.Path;
import com.davfx.ninio.http.service.annotations.PathParameter;
import com.davfx.ninio.http.service.annotations.QueryParameter;
import com.davfx.ninio.http.service.annotations.Route;
import com.davfx.ninio.util.SerialExecutor;
import com.google.common.base.Charsets;

public final class ReadmeWithAnnotatedHttpService {

	@Path("/a")
	public static final class TestController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/echo")
		public Http echo(@QueryParameter("message") String message) {
			return Http.ok().content("a/echo " + message);
		}
	}
	
	@Path("/post")
	public static final class TestPostController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/s")
		public Http echo() {
			// return Http.ok().contentType("text/html" + HttpHeaderExtension.append(HttpHeaderKey.CHARSET, Charsets.UTF_8.name())).content("<html><body><form method='post' action='/post' enctype='multipart/form-data'><input name='text' type='text' value='TEXT'></input><input name='text2' type='text' value='TEXT2'></input><input name='file' type='file'></input><input type='submit' value='Submit'></input></form></body></html>");
			return Http.ok().contentType(HttpContentType.html())
					.content("<html>"
							+ "<body>"
							+ "<form method='post' action='/post/r'>"
							+ "<input name='text' type='text' value='TEXT'></input>"
							+ "<input name='text2' type='text' value='TEXT2'>"
							+ "<input type='submit' name='submit' value='Submit'></input>"
							+ "</form>"
							+ "<form method='get' action='/post/r'>"
							+ "<input name='text' type='text' value='TEXT'></input>"
							+ "<input name='text2' type='text' value='TEXT2'>"
							+ "<input type='submit' name='submit' value='Submit'></input>"
							+ "<input type='submit' name='submit2' value='Submit2'></input>"
							+ "</form>"
							+ "</body>"
							+ "</html>");
		}
		@Route(method = HttpMethod.POST, path = "/r")
		public Http echoPostAll(@BodyParameter("text") String text, HttpPost post) {
			return Http.ok().content("post " + text + " " + post + " " + post.parameters());
		}
		@Route(method = HttpMethod.GET, path = "/r?submit=Submit")
		public Http echoGetAll(HttpServiceRequest r) {
			return Http.ok().content("GET " + r);
		}
		@Route(method = HttpMethod.GET, path = "/r?submit2")
		public Http echoGetFirst(HttpServiceRequest r) {
			return Http.ok().content("GET2 " + r);
		}
	}
	
	@Path("/b")
	public static final class SimpleTestController implements HttpController {
		@Route(method = HttpMethod.GET)
		public Http echo(@QueryParameter("message") String message) {
			return Http.ok().content("b " + message);
		}
	}

	@Path("/e")
	public static final class SimpleEchoHelloWorldController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/{message}/{to}")
		public Http echo(@PathParameter("message") String message, @PathParameter("to") String to) {
			return Http.ok().content(message + " " + to);
		}
	}

	@Path("/ech")
	public static final class SimpleHelloWorldController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/{message}")
		public Http echo(@PathParameter("message") String message, @QueryParameter("to") String to) {
			return Http.ok().content(message + " " + to);
		}
	}

	@Path("/echo")
	public static final class EchoHelloWorldController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/{message}/{to}")
		public Http echo(final @PathParameter("message") String message, final @PathParameter("to") String to, final @QueryParameter("n") String n) throws IOException {
			StringBuilder b = new StringBuilder();
			int nn = Integer.parseInt(n);
			for (int i = 0; i < nn; i++) {
				b.append(message + " " + to + "\n");
			}
			return Http.ok().contentType(HttpContentType.plainText(Charsets.UTF_8)).stream(new ByteArrayInputStream(b.toString().getBytes(Charsets.UTF_8)));
		}
	}

	@Path("/header")
	public static final class EchoHeaderController implements HttpController {
		@Route(method = HttpMethod.GET)
		public Http echo(@HeaderParameter("Host") String host) throws IOException {
			return Http.ok().content(host);
		}
	}

	@Path("/headerWithDefaultValue")
	public static final class EchoHeaderWithDefaultValueController implements HttpController {
		@Route(method = HttpMethod.GET)
		public Http echo(@HeaderParameter("Host2") @DefaultValue("default") String host) throws IOException {
			return Http.ok().content(host);
		}
	}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {
		int port = 8080;
		try (Ninio ninio = Ninio.create()) {
			Annotated.Builder a = Annotated.builder(HttpService.builder());
			for (Class<?> clazz : ReadmeWithAnnotatedHttpService.class.getDeclaredClasses()) {
				if (Arrays.asList(clazz.getInterfaces()).contains(HttpController.class)) {
					a.register((Class<? extends HttpController>) clazz);
				}
			}
	
			try (Listener tcp = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)))) {
				tcp.listen(HttpListening.builder().with(new SerialExecutor(ReadmeWithAnnotatedHttpService.class)).with(a.build()).build());
	
				System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/a/echo?message=helloworld");
				System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/b?message=helloworld");
				System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/e/Hello/World");
				System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/ech/Hello?to=World");
				System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/echo/hello/world?n=100");
				System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/header");
				System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/headerWithDefaultValue");
				System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/post/s");
				System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/files");
				//System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/files/ws.html");
				Thread.sleep(60000);
			}
		}
	}

}
