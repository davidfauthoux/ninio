package com.davfx.ninio.http;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.http.util.AnnotatedHttpService;
import com.davfx.ninio.http.util.HttpController;
import com.davfx.ninio.http.util.annotations.Intercept;
import com.davfx.ninio.http.util.annotations.Path;
import com.davfx.ninio.http.util.annotations.QueryParameter;
import com.davfx.ninio.http.util.annotations.Route;
import com.davfx.ninio.http.util.controllers.CrossDomain;
import com.davfx.ninio.http.util.controllers.Jsonp;
import com.google.common.base.Charsets;
import com.google.gson.JsonPrimitive;

public class HttpServiceInterceptorTest {
	
	static {
		System.setProperty("http.keepAlive", "false");
	}

	public static final class TestInterceptorBeforeController implements HttpController {
		@Route(method = HttpMethod.GET)
		public Http checkMessage(@QueryParameter("check") String check) {
			if ((check != null) && check.equals("bepolite")) {
				return Http.ok();
			} else {
				return Http.internalServerError();
			}
		}
	}
	
	@Path("/get")
	public static final class TestGetWithQueryParameterController implements HttpController {
		@Intercept(TestInterceptorBeforeController.class)
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") String message) {
			return Http.ok().content("GET hello:" + message);
		}
	}
	
	@Test
	public void testGetWithQueryParameter() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				server.register(TestGetWithQueryParameterController.class);

				queue.finish().waitFor();

				{
					HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/get/hello?message=world").openConnection();
					Assertions.assertThat(c.getResponseCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
					c.disconnect();
				}
				{
					HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/get/hello?message=world&check=love").openConnection();
					Assertions.assertThat(c.getResponseCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
					c.disconnect();
				}

				{
					HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/get/hello?message=world&check=bepolite").openConnection();
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
			}
			queue.finish().waitFor();
		}
	}
	
	public static final class JsonpInterceptorHttpController implements HttpController {
		@Route(method = HttpMethod.GET)
		public Http checkMessage(final @QueryParameter("jsonp") String jsonp) {
			if (jsonp == null) {
				return null;
			}
			return Http.wrap(new HttpWrap() {
				@Override
				public void handle(Http http) throws Exception {
					http.contentType(HttpHeaderValue.simple("application/javascript")).content(jsonp + "(" + http.content() + ");");
				}
			});
		}
	}
	
	@Path("/get")
	public static final class TestGetWithQueryParameterWrappedController implements HttpController {
		@Intercept(JsonpInterceptorHttpController.class)
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") String message) {
			return Http.ok().contentType(HttpContentType.json()).content(new JsonPrimitive(message).toString());
		}
	}
	
	@Test
	public void testGetWithQueryParameterWrapped() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				server.register(TestGetWithQueryParameterWrappedController.class);

				queue.finish().waitFor();

				{
					HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/get/hello?message=helloworld").openConnection();
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
					Assertions.assertThat(c.getHeaderField(HttpHeaderKey.CONTENT_TYPE)).isEqualTo("application/json;charset=UTF-8");
					Assertions.assertThat(b.toString()).isEqualTo("\"helloworld\"\n");
				}
				{
					HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/get/hello?message=helloworld&jsonp=f").openConnection();
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
					Assertions.assertThat(c.getHeaderField(HttpHeaderKey.CONTENT_TYPE)).isEqualTo("application/javascript");
					Assertions.assertThat(b.toString()).isEqualTo("f(\"helloworld\");\n");
				}
			}
			queue.finish().waitFor();
		}
	}
	
	@Path("/get")
	public static final class TestGetWithQueryParameterWrappedGloballyController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") String message) {
			return Http.ok().contentType(HttpContentType.json()).content(new JsonPrimitive(message).toString());
		}
	}
	
	@Test
	public void testGetWithQueryParameterWrappedGlobally() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				server.intercept(Jsonp.class);
				server.register(TestGetWithQueryParameterWrappedGloballyController.class);

				queue.finish().waitFor();

				HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/get/hello?message=helloworld&jsonp=f").openConnection();
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
				Assertions.assertThat(c.getHeaderField(HttpHeaderKey.CONTENT_TYPE)).isEqualTo("application/javascript");
				Assertions.assertThat(b.toString()).isEqualTo("f(\"helloworld\");\n");
			}
			queue.finish().waitFor();
		}
	}
	

	@Path("/get")
	public static final class TestGetWithQueryParameterWrappedGloballyStreamController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(final @QueryParameter("message") String message) {
			return Http.ok().contentType(HttpContentType.json()).stream(new HttpStream() {
				@Override
				public void produce(OutputStream output) throws Exception {
					output.write(new JsonPrimitive(message).toString().getBytes(Charsets.UTF_8));
				}
			});
		}
	}
	
	@Test
	public void testGetWithQueryParameterWrappedGloballyStream() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				server.intercept(Jsonp.class);
				server.register(TestGetWithQueryParameterWrappedGloballyStreamController.class);

				queue.finish().waitFor();

				HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/get/hello?message=helloworld&jsonp=f").openConnection();
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
				Assertions.assertThat(c.getHeaderField(HttpHeaderKey.CONTENT_TYPE)).isEqualTo("application/javascript");
				Assertions.assertThat(b.toString()).isEqualTo("f(\"helloworld\");\n");
			}
			queue.finish().waitFor();
		}
	}
	
	@Path("/get")
	public static final class TestGetCrossDomainController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") String message) {
			return Http.ok().content("GET hello:" + message);
		}
	}
	
	@Test
	public void testGetCrossDomain() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				server.intercept(CrossDomain.class);
				server.register(TestGetCrossDomainController.class);

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
				Assertions.assertThat(c.getHeaderField("Access-Control-Allow-Origin")).isEqualTo("*");
				Assertions.assertThat(c.getHeaderField("Access-Control-Allow-Methods")).isEqualTo("GET,PUT,POST,DELETE,HEAD");
			}
			queue.finish().waitFor();
		}
	}
}
