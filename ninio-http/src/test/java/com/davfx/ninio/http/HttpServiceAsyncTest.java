package com.davfx.ninio.http;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;

import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.http.service.HttpController;
import com.davfx.ninio.http.service.annotations.Path;
import com.davfx.ninio.http.service.annotations.QueryParameter;
import com.davfx.ninio.http.service.annotations.Route;
import com.davfx.ninio.http.service.controllers.Jsonp;

public class HttpServiceAsyncTest {
	
	@After
	public void waitALittleBit() throws Exception {
		Thread.sleep(100);
	}
	
	@Path("/get")
	public static final class TestGetWithQueryParameterController implements HttpController {
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(final @QueryParameter("message") String message) {
			return Http.ok().async(new HttpAsync() {
				@Override
				public void produce(HttpAsyncOutput output) {
					output.produce("GET hello:" + message).finish();
				}
			});
		}
	}
	@Test
	public void testGetWithQueryParameter() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, 8080, new TestUtils.ControllerVisitor(TestGetWithQueryParameterController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?message=world")).isEqualTo("text/plain; charset=UTF-8/GET hello:world\n");
			}
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
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, 8080, new TestUtils.InterceptorControllerVisitor(TestInterceptorBeforeController.class, TestGetWithQueryParameterController.class))) {
				try {
					Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?message=world")).isEqualTo("");
					Assertions.fail("Should fail");
				} catch (Exception e) {
				}
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?message=world&check=bepolite")).isEqualTo("text/plain; charset=UTF-8/GET hello:world\n");
			}
		}
	}

	@Test
	public void testGetWithQueryParameterInterceptedWrap() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable server = TestUtils.server(ninio, 8080, new TestUtils.InterceptorControllerVisitor(Jsonp.class, TestGetWithQueryParameterController.class))) {
				Assertions.assertThat(TestUtils.get("http://127.0.0.1:8080/get/hello?message=world&jsonp=f")).isEqualTo("text/plain; charset=UTF-8/f(GET hello:world);\n");
			}
		}
	}
}
