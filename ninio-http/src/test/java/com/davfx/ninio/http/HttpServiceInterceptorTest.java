package com.davfx.ninio.http;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;

import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.http.service.HttpContentType;
import com.davfx.ninio.http.service.HttpController;
import com.davfx.ninio.http.service.annotations.Intercept;
import com.davfx.ninio.http.service.annotations.Path;
import com.davfx.ninio.http.service.annotations.QueryParameter;
import com.davfx.ninio.http.service.annotations.Route;
import com.davfx.ninio.http.service.controllers.CrossDomain;
import com.davfx.ninio.http.service.controllers.Jsonp;
import com.google.common.base.Charsets;
import com.google.gson.JsonPrimitive;

public class HttpServiceInterceptorTest {
	
	static {
		System.setProperty("http.keepAlive", "false");
	}

	@After
	public void waitALittleBit() throws Exception {
		Thread.sleep(100);
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
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, 8080, new TestUtils.ControllerVisitor(TestGetWithQueryParameterController.class))) {
				try {
					TestUtils.get("http://127.0.0.1:8080/get/hello?message=world");
					Assertions.fail("Should fail");
				} catch (Exception e) {
				}
				try {
					TestUtils.get("http://127.0.0.1:8080/get/hello?message=world&check=love");
					Assertions.fail("Should fail");
				} catch (Exception e) {
				}
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?message=world&check=bepolite")).isEqualTo("text/plain; charset=UTF-8/GET hello:world\n");
			}
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
					http.contentType("application/javascript").content(jsonp + "(" + http.content() + ");");
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
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, 8080, new TestUtils.ControllerVisitor(TestGetWithQueryParameterWrappedController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?message=helloworld")).isEqualTo("application/json; charset=UTF-8/\"helloworld\"\n");
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?message=helloworld&jsonp=f")).isEqualTo("application/javascript/f(\"helloworld\");\n");
			}
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
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, 8080, new TestUtils.InterceptorControllerVisitor(Jsonp.class, TestGetWithQueryParameterWrappedGloballyController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?message=helloworld&jsonp=f")).isEqualTo("application/javascript/f(\"helloworld\");\n");
			}
		}
	}
	

	@Path("/get")
	public static final class TestGetWithQueryParameterWrappedGloballyStreamController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(final @QueryParameter("message") String message) {
			return Http.ok().contentType(HttpContentType.json()).stream(new ByteArrayInputStream(new JsonPrimitive(message).toString().getBytes(Charsets.UTF_8)));
		}
	}
	
	@Test
	public void testGetWithQueryParameterWrappedGloballyStream() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, 8080, new TestUtils.InterceptorControllerVisitor(Jsonp.class, TestGetWithQueryParameterWrappedGloballyStreamController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?message=helloworld&jsonp=f")).isEqualTo("application/javascript/f(\"helloworld\");\n");
			}
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
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, 8080, new TestUtils.InterceptorControllerVisitor(CrossDomain.class, TestGetCrossDomainController.class))) {
				HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/get/hello?message=world").openConnection();
				Assertions.assertThat(c.getHeaderField("Access-Control-Allow-Origin")).isEqualTo("*");
				Assertions.assertThat(c.getHeaderField("Access-Control-Allow-Methods")).isEqualTo("GET, PUT, POST, DELETE, HEAD");
				c.disconnect();
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?message=world")).isEqualTo("text/plain; charset=UTF-8/GET hello:world\n");
			}
		}
	}
}
