package com.davfx.ninio.http;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;

import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.http.service.HttpController;
import com.davfx.ninio.http.service.annotations.Assets;
import com.davfx.ninio.http.service.annotations.Path;
import com.davfx.ninio.http.service.annotations.QueryParameter;
import com.davfx.ninio.http.service.annotations.Route;

public class HttpServiceSimpleTest {
	
	@After
	public void waitALittleBit() throws Exception {
		Thread.sleep(100);
	}
	
	//@Headers({@Header(key = "User-Agent", pattern = "Java/1.7.0_11")})
	@Path("/get")
	@Assets(path = "", index = "index2.html")
	public static final class TestGetWithQueryParameterController implements HttpController {
		//@Headers({@Header(key = "Host", pattern = "127\\.0\\.0\\.1\\:8080")})
		@Route(method = HttpMethod.GET, path = "/hello")
		public Http echo(@QueryParameter("message") String message) {
			return Http.ok().content("GET hello:" + message);
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
}
