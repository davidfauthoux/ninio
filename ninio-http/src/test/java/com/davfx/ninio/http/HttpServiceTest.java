package com.davfx.ninio.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.http.util.AnnotatedHttpService;
import com.davfx.ninio.http.util.HttpController;
import com.davfx.ninio.http.util.annotations.BodyParameter;
import com.davfx.ninio.http.util.annotations.DefaultValue;
import com.davfx.ninio.http.util.annotations.Header;
import com.davfx.ninio.http.util.annotations.Path;
import com.davfx.ninio.http.util.annotations.PathParameter;
import com.davfx.ninio.http.util.annotations.QueryParameter;
import com.davfx.ninio.http.util.annotations.Route;
import com.google.common.base.Charsets;

public class HttpServiceTest {
	
	@Path("/get")
	public static final class TestGetWithQueryParameterController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") String message) {
			return Http.ok().content("GET hello:" + message);
		}
	}
	@Path("/getpath")
	public static final class TestGetWithPathParameterController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello/{message}/a")
		public Http echo(@PathParameter("message") String message) {
			return Http.ok().content("GET hello:" + message);
		}
	}
	@Path("/post")
	public static final class TestPostWithBodyParameterController implements HttpController {
		@Route(method = HttpMethod.POST, path = "/hello")
		public Http echo(@BodyParameter("message") String message) {
			return Http.ok().content("POST hello:" + message);
		}
	}
	@Path("/getheader")
	public static final class TestGetWithHeaderController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@Header("Host") String host) {
			return Http.ok().content("GET Host:" + host);
		}
	}
	@Path("/getwithdefault")
	public static final class TestGetWithQueryParameterDefaultValueController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") @DefaultValue("www") String message) {
			return Http.ok().content("GET hello:" + message);
		}
	}
	@Path("/getfork")
	public static final class TestGetForkController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello/{a}/fork0")
		public Http echo0(@PathParameter("a") String a) {
			return Http.ok().content("GET0 hello:" + a);
		}
		@Route(method = HttpMethod.GET, path = "/hello/{a}/fork1")
		public Http echo1(@PathParameter("a") String a) {
			return Http.ok().content("GET1 hello:" + a);
		}
	}
	@Path("/getparamfork")
	public static final class TestGetForkWithQueryController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello/{a}?fork0")
		public Http echo0(@PathParameter("a") String a) {
			return Http.ok().content("GET0 hello:" + a);
		}
		@Route(method = HttpMethod.GET, path = "/hello/{a}?fork1")
		public Http echo1(@PathParameter("a") String a) {
			return Http.ok().content("GET1 hello:" + a);
		}
		@Route(method = HttpMethod.GET, path = "/hello/{a}?fork2=f")
		public Http echo2(@PathParameter("a") String a) {
			return Http.ok().content("GET2f hello:" + a);
		}
		@Route(method = HttpMethod.GET, path = "/hello/{a}?fork2=g")
		public Http echo3(@PathParameter("a") String a) {
			return Http.ok().content("GET2g hello:" + a);
		}
	}
	@Path("/getstream")
	public static final class TestGetStreamController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/{message}/{to}")
		public Http echo(final @PathParameter("message") String message, final @PathParameter("to") String to, final @QueryParameter("n") String n) throws IOException {
			return Http.ok().contentType(HttpContentType.plainText(Charsets.UTF_8)).stream(new HttpStream() {
				@Override
				public void produce(OutputStreamFactory output) throws Exception {
					int nn = Integer.parseInt(n);
					try (OutputStream out = output.open()) {
						for (int i = 0; i < nn; i++) {
							out.write(("GET " + message + ":" + to + "\n").getBytes(Charsets.UTF_8));
						}
					}
				}
			});
		}
	}

	@Test
	public void testGetWithQueryParameter() throws Exception {
		try (AnnotatedHttpService server = new AnnotatedHttpService(new Address(Address.ANY, 8080))) {
			for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
		    	@SuppressWarnings("unchecked")
				Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
				server.register(c);
			}
			
			Thread.sleep(100);
			
			HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/get/hello?message=world").openConnection();
			StringBuilder b = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), Charsets.UTF_8))) {
				while (true) {
					String line = r.readLine();
					if (line == null) {
						break;
					}
					b.append(line).append('\n');
				}
			}
			c.disconnect();
			Assertions.assertThat(b.toString()).isEqualTo("GET hello:world\n");

			Thread.sleep(100);
		}
	}
	
	@Test
	public void testGetWithPathParameter() throws Exception {
		try (AnnotatedHttpService server = new AnnotatedHttpService(new Address(Address.ANY, 8080))) {
			for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
		    	@SuppressWarnings("unchecked")
				Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
				server.register(c);
			}
			
			Thread.sleep(100);
			
			HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/getpath/hello/world/a").openConnection();
			StringBuilder b = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), Charsets.UTF_8))) {
				while (true) {
					String line = r.readLine();
					if (line == null) {
						break;
					}
					b.append(line).append('\n');
				}
			}
			c.disconnect();
			Assertions.assertThat(b.toString()).isEqualTo("GET hello:world\n");

			Thread.sleep(100);
		}
	}
	
	@Test
	public void testPostWithBodyParameter() throws Exception {
		try (AnnotatedHttpService server = new AnnotatedHttpService(new Address(Address.ANY, 8080))) {
			for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
		    	@SuppressWarnings("unchecked")
				Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
				server.register(c);
			}
			
			Thread.sleep(100);
			
			HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/post/hello").openConnection();
			c.setDoOutput(true);
			try (Writer w = new OutputStreamWriter(c.getOutputStream())) {
				w.write("message=world");
			}
			StringBuilder b = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), Charsets.UTF_8))) {
				while (true) {
					String line = r.readLine();
					if (line == null) {
						break;
					}
					b.append(line).append('\n');
				}
			}
			c.disconnect();
			Assertions.assertThat(b.toString()).isEqualTo("POST hello:world\n");
			
			Thread.sleep(100);
		}
	}

	@Test
	public void testGetWithHeader() throws Exception {
		try (AnnotatedHttpService server = new AnnotatedHttpService(new Address(Address.ANY, 8080))) {
			for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
		    	@SuppressWarnings("unchecked")
				Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
				server.register(c);
			}
			
			Thread.sleep(100);
			
			HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/getheader/hello").openConnection();
			StringBuilder b = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), Charsets.UTF_8))) {
				while (true) {
					String line = r.readLine();
					if (line == null) {
						break;
					}
					b.append(line).append('\n');
				}
			}
			c.disconnect();
			Assertions.assertThat(b.toString()).isEqualTo("GET Host:127.0.0.1:8080\n");

			Thread.sleep(100);
		}
	}

	@Test
	public void testGetWithQueryParameterDefaultValue() throws Exception {
		try (AnnotatedHttpService server = new AnnotatedHttpService(new Address(Address.ANY, 8080))) {
			for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
		    	@SuppressWarnings("unchecked")
				Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
				server.register(c);
			}
			
			Thread.sleep(100);
			
			HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/getwithdefault/hello").openConnection();
			StringBuilder b = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), Charsets.UTF_8))) {
				while (true) {
					String line = r.readLine();
					if (line == null) {
						break;
					}
					b.append(line).append('\n');
				}
			}
			c.disconnect();
			Assertions.assertThat(b.toString()).isEqualTo("GET hello:www\n");

			Thread.sleep(100);
		}
	}

	@Test
	public void testGetForkParameter() throws Exception {
		try (AnnotatedHttpService server = new AnnotatedHttpService(new Address(Address.ANY, 8080))) {
			for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
		    	@SuppressWarnings("unchecked")
				Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
				server.register(c);
			}
			
			Thread.sleep(100);
			
			HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/getfork/hello/world/fork1").openConnection();
			StringBuilder b = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), Charsets.UTF_8))) {
				while (true) {
					String line = r.readLine();
					if (line == null) {
						break;
					}
					b.append(line).append('\n');
				}
			}
			c.disconnect();
			Assertions.assertThat(b.toString()).isEqualTo("GET1 hello:world\n");

			c = (HttpURLConnection) new URL("http://127.0.0.1:8080/getfork/hello/world/fork0").openConnection();
			b = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), Charsets.UTF_8))) {
				while (true) {
					String line = r.readLine();
					if (line == null) {
						break;
					}
					b.append(line).append('\n');
				}
			}
			c.disconnect();
			Assertions.assertThat(b.toString()).isEqualTo("GET0 hello:world\n");

			Thread.sleep(100);
		}
	}
	
	@Test
	public void testGetForkWithQueryParameter() throws Exception {
		try (AnnotatedHttpService server = new AnnotatedHttpService(new Address(Address.ANY, 8080))) {
			for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
		    	@SuppressWarnings("unchecked")
				Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
				server.register(c);
			}
			
			Thread.sleep(100);
			
			HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/getparamfork/hello/world?fork1").openConnection();
			StringBuilder b = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), Charsets.UTF_8))) {
				while (true) {
					String line = r.readLine();
					if (line == null) {
						break;
					}
					b.append(line).append('\n');
				}
			}
			c.disconnect();
			Assertions.assertThat(b.toString()).isEqualTo("GET1 hello:world\n");

			c = (HttpURLConnection) new URL("http://127.0.0.1:8080/getparamfork/hello/world?fork0").openConnection();
			b = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), Charsets.UTF_8))) {
				while (true) {
					String line = r.readLine();
					if (line == null) {
						break;
					}
					b.append(line).append('\n');
				}
			}
			c.disconnect();
			Assertions.assertThat(b.toString()).isEqualTo("GET0 hello:world\n");

			c = (HttpURLConnection) new URL("http://127.0.0.1:8080/getparamfork/hello/world?fork0=f").openConnection();
			b = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), Charsets.UTF_8))) {
				while (true) {
					String line = r.readLine();
					if (line == null) {
						break;
					}
					b.append(line).append('\n');
				}
			}
			c.disconnect();
			Assertions.assertThat(b.toString()).isEqualTo("GET0 hello:world\n");

			c = (HttpURLConnection) new URL("http://127.0.0.1:8080/getparamfork/hello/world?fork2=f").openConnection();
			b = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), Charsets.UTF_8))) {
				while (true) {
					String line = r.readLine();
					if (line == null) {
						break;
					}
					b.append(line).append('\n');
				}
			}
			c.disconnect();
			Assertions.assertThat(b.toString()).isEqualTo("GET2f hello:world\n");

			c = (HttpURLConnection) new URL("http://127.0.0.1:8080/getparamfork/hello/world?fork2=g").openConnection();
			b = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), Charsets.UTF_8))) {
				while (true) {
					String line = r.readLine();
					if (line == null) {
						break;
					}
					b.append(line).append('\n');
				}
			}
			c.disconnect();
			Assertions.assertThat(b.toString()).isEqualTo("GET2g hello:world\n");

			Thread.sleep(100);
		}
	}
	
	@Test
	public void testGetStreamParameter() throws Exception {
		try (AnnotatedHttpService server = new AnnotatedHttpService(new Address(Address.ANY, 8080))) {
			for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
		    	@SuppressWarnings("unchecked")
				Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
				server.register(c);
			}
			
			Thread.sleep(100);
			
			HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/getstream/hello/world?n=3").openConnection();
			StringBuilder b = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), Charsets.UTF_8))) {
				while (true) {
					String line = r.readLine();
					if (line == null) {
						break;
					}
					b.append(line).append('\n');
				}
			}
			c.disconnect();
			Assertions.assertThat(b.toString()).isEqualTo("GET hello:world\nGET hello:world\nGET hello:world\n");

			Thread.sleep(100);
		}
	}
	
}
