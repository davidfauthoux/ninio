package com.davfx.ninio.http;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.http.util.AnnotatedHttpService;
import com.davfx.ninio.http.util.HttpController;
import com.davfx.ninio.http.util.annotations.Path;
import com.davfx.ninio.http.util.annotations.QueryParameter;
import com.davfx.ninio.http.util.annotations.Route;
import com.davfx.ninio.http.util.controllers.Jsonp;
import com.google.common.base.Charsets;

public class HttpServiceAsyncTest {
	
	static {
		System.setProperty("http.keepAlive", "false");
	}
	
	@Path("/get")
	public static final class TestGetWithQueryParameterController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(final @QueryParameter("message") String message) {
			return Http.ok().async(new HttpAsync() {
				@Override
				public void produce(HttpAsyncOutput output) {
					output.produce("GET hello:" + message).close();
				}
			});
		}
	}
	@Test
	public void testGetWithQueryParameter() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
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
	
	@Test
	public void testGetWithQueryParameterIntercepted() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				server.intercept(TestInterceptorBeforeController.class);
				server.register(TestGetWithQueryParameterController.class);

				queue.finish().waitFor();
				
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
			queue.finish().waitFor();
		}
	}

	@Test
	public void testGetWithQueryParameterInterceptedWrap() throws Exception {
		try (Queue queue = new Queue()) {
			try (AnnotatedHttpService server = new AnnotatedHttpService(queue, new Address(Address.ANY, 8080))) {
				server.intercept(Jsonp.class);
				server.register(TestGetWithQueryParameterController.class);

				queue.finish().waitFor();
				
				HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/get/hello?message=world&jsonp=f").openConnection();
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
				Assertions.assertThat(b.toString()).isEqualTo("f(GET hello:world);\n");
			}
			queue.finish().waitFor();
		}
	}

	

}
