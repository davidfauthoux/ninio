package com.davfx.ninio.http;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.http.util.AnnotatedHttpService;
import com.davfx.ninio.http.util.HttpController;
import com.davfx.ninio.http.util.HttpPost;
import com.davfx.ninio.http.util.HttpServiceResult;
import com.davfx.ninio.http.util.annotations.DefaultValue;
import com.davfx.ninio.http.util.annotations.Header;
import com.davfx.ninio.http.util.annotations.Path;
import com.davfx.ninio.http.util.annotations.PathParameter;
import com.davfx.ninio.http.util.annotations.QueryParameter;
import com.davfx.ninio.http.util.annotations.Route;
import com.davfx.util.Wait;
import com.google.common.base.Charsets;
import com.google.common.reflect.ClassPath;

public final class ReadmeWithAnnotatedHttpService {

	/*
	@HttpController()
	public static final class VerySimpleTestController {
		@HttpRoute()
		public void echo(@HttpQueryParameter("message") String message, HttpServiceResult result) {
			result.success(HttpContentType.plainText(), " " + message);
		}
	}
	*/

	@Path("/a")
	public static final class TestController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/echo")
		public void echo(@QueryParameter("message") String message, HttpRequest request, HttpServiceResult result) {
			result.out("a/echo " + message);
		}
	}
	
	@Path("/post")
	public static final class TestPostController implements HttpController {
		@Route(method = HttpMethod.GET)
		public void echo(HttpServiceResult result) {
			result.contentType("text/html").out("<html><body><form method='post'><input name='text' type='text' value='TEXT'><input name='text2' type='text' value='TEXT2'></input><input type='submit' value='Submit'></input></form></body></html>");
		}
		@Route(method = HttpMethod.POST)
		public void echo(HttpPost post, HttpServiceResult result) {
			result.out("post " + post);
		}
	}
	
	@Path("/b")
	public static final class SimpleTestController implements HttpController {
		@Route(method = HttpMethod.GET)
		public void echo(@QueryParameter("message") String message, HttpServiceResult result) {
			result.out("b " + message);
		}
	}

	@Path("/e")
	public static final class SimpleEchoHelloWorldController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/{message}/{to}")
		public void echo(@PathParameter("message") String message, @PathParameter("to") String to, HttpServiceResult result) {
			result.out(message + " " + to);
		}
	}

	@Path("/ech")
	public static final class SimpleHelloWorldController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/{message}")
		public void echo(@PathParameter("message") String message, @QueryParameter("to") String to, HttpServiceResult result) {
			result.out(message + " " + to);
		}
	}

	@Path("/echo")
	public static final class EchoHelloWorldController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/{message}/{to}")
		public void echo(@PathParameter("message") String message, @PathParameter("to") String to, @QueryParameter("n") String n, HttpServiceResult result) throws IOException {
			int nn = Integer.parseInt(n);
			try (OutputStream out = result.contentType(HttpContentType.plainText(Charsets.UTF_8)).out()) {
				for (int i = 0; i < nn; i++) {
					out.write((message + " " + to + "\n").getBytes(Charsets.UTF_8));
				}
			}
		}
	}
	
	@Path("/header")
	public static final class EchoHeaderController implements HttpController {
		@Route(method = HttpMethod.GET)
		public void echo(@Header("Host") String host, HttpServiceResult result) throws IOException {
			result.out(host);
		}
	}

	@Path("/headerWithDefaultValue")
	public static final class EchoHeaderWithDefaultValueController implements HttpController {
		@Route(method = HttpMethod.GET)
		public void echo(@Header("Host2") @DefaultValue("default") String host, HttpServiceResult result) throws IOException {
			result.out(host);
		}
	}

	public static void main(String[] args) throws Exception {
		Wait wait = new Wait();
		int port = 8080;
		try (AnnotatedHttpService server = new AnnotatedHttpService()) {
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

			server.start(port);

			// System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/?message=helloworld");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/a/echo?message=helloworld");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/b?message=helloworld");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/e/Hello/World");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/ech/Hello?to=World");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/echo/hello/world?n=100");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/header");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/headerWithDefaultValue");
			System.out.println("POST http://" + new Address(Address.LOCALHOST, port) + "/post");
			wait.waitFor();
		}
	}

}
