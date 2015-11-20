package com.davfx.ninio.http;

import java.io.BufferedReader;
import java.io.File;
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
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.http.util.AnnotatedHttpService;
import com.davfx.ninio.http.util.HttpController;
import com.davfx.ninio.http.util.annotations.BodyParameter;
import com.davfx.ninio.http.util.annotations.DefaultValue;
import com.davfx.ninio.http.util.annotations.Header;
import com.davfx.ninio.http.util.annotations.HeaderParameter;
import com.davfx.ninio.http.util.annotations.Path;
import com.davfx.ninio.http.util.annotations.PathParameter;
import com.davfx.ninio.http.util.annotations.QueryParameter;
import com.davfx.ninio.http.util.annotations.Route;
import com.davfx.ninio.http.util.controllers.Assets;
import com.google.common.base.Charsets;

public class HttpServiceTest {
	
	static {
		System.setProperty("http.keepAlive", "false");
	}
	
	@Path("/get")
	public static final class TestGetWithQueryParameterController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") String message) {
			return Http.ok().content("GET hello:" + message);
		}
	}
	@Test
	public void testGetWithQueryParameter() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				/*
				for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
			    	@SuppressWarnings("unchecked")
					Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
					server.register(c);
				}
				*/
				server.register(TestGetWithQueryParameterController.class);

				queue.finish().waitFor();
				
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
			}
			queue.finish().waitFor();
		}
	}
	
	@Path("/getpath")
	public static final class TestGetWithPathParameterController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello/{message}/a")
		public Http echo(@PathParameter("message") String message) {
			return Http.ok().content("GET hello:" + message);
		}
	}

	@Test
	public void testGetWithPathParameter() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				/*
				for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
			    	@SuppressWarnings("unchecked")
					Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
					server.register(c);
				}
				*/
				server.register(TestGetWithPathParameterController.class);
				
				queue.finish().waitFor();
				
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
			}
			queue.finish().waitFor();
		}
	}
	
	@Path("/post")
	public static final class TestPostWithBodyParameterController implements HttpController {
		@Route(method = HttpMethod.POST, path = "/hello")
		public Http echo(@BodyParameter("message") String message) {
			return Http.ok().content("POST hello:" + message);
		}
	}

	@Test
	public void testPostWithBodyParameter() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				/*
				for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
			    	@SuppressWarnings("unchecked")
					Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
					server.register(c);
				}
				*/
				server.register(TestPostWithBodyParameterController.class);

				queue.finish().waitFor();
				
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
			}
			queue.finish().waitFor();
		}
	}

	@Path("/getheader")
	public static final class TestGetWithHeaderController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@HeaderParameter("Host") String host) {
			return Http.ok().content("GET Host:" + host);
		}
	}
	@Test
	public void testGetWithHeader() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				/*
				for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
			    	@SuppressWarnings("unchecked")
					Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
					server.register(c);
				}
				*/
				server.register(TestGetWithHeaderController.class);
				
				queue.finish().waitFor();
				
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
			}
			queue.finish().waitFor();
		}
	}

	@Path("/getwithdefault")
	public static final class TestGetWithQueryParameterDefaultValueController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") @DefaultValue("www") String message) {
			return Http.ok().content("GET hello:" + message);
		}
	}

	@Test
	public void testGetWithQueryParameterDefaultValue() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				/*
				for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
			    	@SuppressWarnings("unchecked")
					Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
					server.register(c);
				}
				*/
				server.register(TestGetWithQueryParameterDefaultValueController.class);
				
				queue.finish().waitFor();
				
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
			}
			queue.finish().waitFor();
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

	@Test
	public void testGetForkParameter() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				/*
				for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
			    	@SuppressWarnings("unchecked")
					Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
					server.register(c);
				}
				*/
				server.register(TestGetForkController.class);
				
				queue.finish().waitFor();
				
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
			}
			queue.finish().waitFor();
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

	@Test
	public void testGetForkWithQueryParameter() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				/*
				for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
			    	@SuppressWarnings("unchecked")
					Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
					server.register(c);
				}
				*/
				server.register(TestGetForkWithQueryController.class);
				
				queue.finish().waitFor();
				
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
			}
			queue.finish().waitFor();
		}
	}
	
	@Path("/getstream")
	public static final class TestGetStreamController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/{message}/{to}")
		public Http echo(final @PathParameter("message") String message, final @PathParameter("to") String to, final @QueryParameter("n") String n) throws IOException {
			return Http.ok().contentType(HttpContentType.plainText(Charsets.UTF_8)).stream(new HttpStream() {
				@Override
				public void produce(OutputStream out) throws Exception {
					int nn = Integer.parseInt(n);
					for (int i = 0; i < nn; i++) {
						out.write(("GET " + message + ":" + to + "\n").getBytes(Charsets.UTF_8));
					}
				}
			});
		}
	}

	@Test
	public void testGetStreamParameter() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				/*
				for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
			    	@SuppressWarnings("unchecked")
					Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
					server.register(c);
				}
				*/
				server.register(TestGetStreamController.class);
				
				queue.finish().waitFor();
				
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
			}
			queue.finish().waitFor();
		}
	}
	
	@Path("/getfilterbyheader")
	@Header(key = "Host", value = "127.0.0.1:8080")
	public static final class TestGetWithHostFilterController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") String message, @HeaderParameter("Host") String host) {
			return Http.ok().content("GET hello:" + message + " " + host);
		}
	}
	@Path("/getfilterbyheader2")
	@Header(key = "Host", value = "127.0.0.1:8081")
	public static final class TestGetWithHostFilterController2 implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") String message, @HeaderParameter("Host") String host) {
			return Http.ok().content("GET hello:" + message + " " + host);
		}
	}

	@Test
	public void testGetWithHostFilter() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				/*
				for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
			    	@SuppressWarnings("unchecked")
					Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
					server.register(c);
				}
				*/
				server.register(TestGetWithHostFilterController.class);
				server.register(TestGetWithHostFilterController2.class);
				
				queue.finish().waitFor();
				
				HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/getfilterbyheader/hello?message=world").openConnection();
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
				Assertions.assertThat(b.toString()).isEqualTo("GET hello:world 127.0.0.1:8080\n");
	
				try (PortRouter router = new PortRouter(new Queue(), new Address(Address.ANY, 8081), new Address(Address.LOCALHOST, 8080), null)) {
					queue.finish().waitFor();
					Thread.sleep(100);
					c = (HttpURLConnection) new URL("http://127.0.0.1:8081/getfilterbyheader2/hello?message=world").openConnection();
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
					Assertions.assertThat(b.toString()).isEqualTo("GET hello:world 127.0.0.1:8081\n");
				}
			}
			queue.finish().waitFor();
		}
	}
	
	@Test
	public void testGetWithHostFilterSame() throws Exception {
		testGetWithHostFilter();
	}
	
	@Test
	public void testFiles() throws Exception {
		int port = 8080;
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, port))) {
				server.register(HttpQueryPath.of("/files"), new Assets(new File("src/test/resources"), "index.html"));
				/*
				for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
			    	@SuppressWarnings("unchecked")
					Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
					server.register(c);
				}
				*/

				queue.finish().waitFor();
				
				HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/files/index.html").openConnection();
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
				Assertions.assertThat(b.toString()).isEqualTo("<!doctype html><html><head><meta charset=\"utf-8\" /></head><body><div>Hello</div></body>\n");
			}
			queue.finish().waitFor();
		}
	}

	@Test
	public void testFilesWithPortRouting() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				server.register(HttpQueryPath.of("/files"), new Assets(new File("src/test/resources"), "index.html"));
				/*
				for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
			    	@SuppressWarnings("unchecked")
					Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
					server.register(c);
				}
				*/

				queue.finish().waitFor();
				
				try (PortRouter router = new PortRouter(queue, new Address(Address.ANY, 8081), new Address(Address.LOCALHOST, 8080), null)) {
					queue.finish().waitFor();
					Thread.sleep(100);
					HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8081/files/").openConnection();
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
					Assertions.assertThat(b.toString()).isEqualTo("<!doctype html><html><head><meta charset=\"utf-8\" /></head><body><div>Hello</div></body>\n");
				}
			}
			queue.finish().waitFor();
		}
	}
	@Test
	public void testInsideFilesWithPortRouting() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				server.register(HttpQueryPath.of("/files"), new Assets(new File("src/test/resources"), "index.html"));
				/*
				for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
			    	@SuppressWarnings("unchecked")
					Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
					server.register(c);
				}
				*/

				queue.finish().waitFor();
				
				try (PortRouter router = new PortRouter(queue, new Address(Address.ANY, 8081), new Address(Address.LOCALHOST, 8080), null)) {
					queue.finish().waitFor();
					Thread.sleep(100);
					HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8081/files/in-dir/in-file.html").openConnection();
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
					Assertions.assertThat(b.toString()).isEqualTo("<!doctype html><html><head><meta charset=\"utf-8\" /></head><body><div>Hello inside</div></body>\n");
				}
			}
			queue.finish().waitFor();
		}
	}
	@Test
	public void testRootIndexFilesWithPortRouting() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				server.register(HttpQueryPath.of(), new Assets(new File("src/test/resources"), "index.html"));
				/*
				for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
			    	@SuppressWarnings("unchecked")
					Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
					server.register(c);
				}
				*/

				queue.finish().waitFor();
				
				try (PortRouter router = new PortRouter(queue, new Address(Address.ANY, 8081), new Address(Address.LOCALHOST, 8080), null)) {
					queue.finish().waitFor();
					Thread.sleep(100);
					HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8081").openConnection();
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
					Assertions.assertThat(b.toString()).isEqualTo("<!doctype html><html><head><meta charset=\"utf-8\" /></head><body><div>Hello</div></body>\n");
				}
			}
			queue.finish().waitFor();
		}
	}
	@Test
	public void testRootInsideFilesWithPortRouting() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				server.register(HttpQueryPath.of(), new Assets(new File("src/test/resources"), "index.html"));
				/*
				for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
			    	@SuppressWarnings("unchecked")
					Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
					server.register(c);
				}
				*/

				queue.finish().waitFor();
				
				try (PortRouter router = new PortRouter(queue, new Address(Address.ANY, 8081), new Address(Address.LOCALHOST, 8080), null)) {
					queue.finish().waitFor();
					Thread.sleep(100);
					HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8081/in-dir/in-file.html").openConnection();
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
					Assertions.assertThat(b.toString()).isEqualTo("<!doctype html><html><head><meta charset=\"utf-8\" /></head><body><div>Hello inside</div></body>\n");
				}
			}
			queue.finish().waitFor();
		}
	}
	
	@Test
	public void testInsideIndexFilesWithPortRouting() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				server.register(HttpQueryPath.of("/files"), new Assets(new File("src/test/resources"), "index.html"));
				/*
				for (Class<?> cls : HttpServiceTest.class.getDeclaredClasses()) {
			    	@SuppressWarnings("unchecked")
					Class<? extends HttpController> c = (Class<? extends HttpController>) cls;
					server.register(c);
				}
				*/

				queue.finish().waitFor();
				
				try (PortRouter router = new PortRouter(queue, new Address(Address.ANY, 8081), new Address(Address.LOCALHOST, 8080), null)) {
					queue.finish().waitFor();
					Thread.sleep(100);
					HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8081/files/in-dir").openConnection();
					Assertions.assertThat(c.getResponseCode()).isEqualTo(404);
					c.disconnect();
				}
			}
			queue.finish().waitFor();
		}
	}
}
