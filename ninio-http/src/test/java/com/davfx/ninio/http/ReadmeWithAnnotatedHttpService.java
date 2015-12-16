package com.davfx.ninio.http;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.http.util.AnnotatedHttpService;
import com.davfx.ninio.http.util.HttpController;
import com.davfx.ninio.http.util.HttpPost;
import com.davfx.ninio.http.util.annotations.BodyParameter;
import com.davfx.ninio.http.util.annotations.DefaultValue;
import com.davfx.ninio.http.util.annotations.HeaderParameter;
import com.davfx.ninio.http.util.annotations.Path;
import com.davfx.ninio.http.util.annotations.PathParameter;
import com.davfx.ninio.http.util.annotations.QueryParameter;
import com.davfx.ninio.http.util.annotations.Route;
import com.davfx.ninio.http.util.controllers.Assets;
import com.davfx.util.Wait;
import com.google.common.base.Charsets;
import com.google.common.reflect.ClassPath;

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
		public Http echoGetAll(HttpRequest r) {
			return Http.ok().content("GET " + r);
		}
		@Route(method = HttpMethod.GET, path = "/r?submit2")
		public Http echoGetFirst(HttpRequest r) {
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
			return Http.ok().contentType(HttpContentType.plainText(Charsets.UTF_8)).stream(new HttpStream() {
				@Override
				public void produce(OutputStream out) throws Exception {
					int nn = Integer.parseInt(n);
					for (int i = 0; i < nn; i++) {
						out.write((message + " " + to + "\n").getBytes(Charsets.UTF_8));
					}
				}
			});
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

	public static void main(String[] args) throws Exception {
		Wait wait = new Wait();
		int port = 8080;
		try (AnnotatedHttpService server = new AnnotatedHttpService(new Queue(), new Address(Address.ANY, port))) {
			server.register(HttpQueryPath.of("/files"), new Assets(new File("src/test/resources"), "index.html"));
			ClassPath classPath = ClassPath.from(ReadmeWithAnnotatedHttpService.class.getClassLoader());
			
			for (ClassPath.ClassInfo classInfo : classPath.getAllClasses()) {
				Class<?> clazz;
				try {
					clazz = classInfo.load();
				} catch (LinkageError e) {
					continue;
				}
				if (Arrays.asList(clazz.getInterfaces()).contains(HttpController.class)) {
					@SuppressWarnings("unchecked")
					Class<? extends HttpController> c = (Class<? extends HttpController>) clazz;
					server.register(c);
				}
			}

			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/a/echo?message=helloworld");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/b?message=helloworld");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/e/Hello/World");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/ech/Hello?to=World");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/echo/hello/world?n=100");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/header");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/headerWithDefaultValue");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/post/s");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/files");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/files/ws.html");
			wait.waitFor();
		}
	}

}
