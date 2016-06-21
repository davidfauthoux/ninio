package com.davfx.ninio.http.v3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.core.ThreadingSerialExecutor;
import com.davfx.ninio.core.WaitListenConnecting;
import com.davfx.ninio.http.HttpListening;
import com.davfx.ninio.http.HttpMethod;
import com.davfx.ninio.http.service.Annotated;
import com.davfx.ninio.http.service.HttpController;
import com.davfx.ninio.http.service.HttpService;
import com.davfx.ninio.http.service.annotations.Assets;
import com.davfx.ninio.http.service.annotations.Path;
import com.davfx.ninio.http.service.annotations.QueryParameter;
import com.davfx.ninio.http.service.annotations.Route;
import com.davfx.ninio.util.Wait;
import com.google.common.base.Charsets;

public class HttpServiceTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpServiceTest.class);

	static {
		System.setProperty("http.keepAlive", "false");
	}
	
	private static Disconnectable server(Ninio ninio, int port, Class<? extends HttpController> clazz) {
		Wait wait = new Wait();
		Disconnectable tcp = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port))
				.connecting(new WaitListenConnecting(wait)).listening(
						HttpListening.builder().with(new ThreadingSerialExecutor(HttpServiceTest.class)).with(
								Annotated.builder(HttpService.builder()).register(clazz).build()
								).build()));
		wait.waitFor();
		return tcp;
	}
	
	private static String get(String url) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		LOGGER.debug("Request headers: {}", c.getRequestProperties());
		if (c.getResponseCode() != 200) {
			throw new IOException("Response error: " + c.getResponseCode());
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
		LOGGER.debug("Response headers: {}", c.getHeaderFields());
		c.disconnect();
		return b.toString();
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
			try (Disconnectable server = server(ninio, 8080, TestGetWithQueryParameterController.class)) {
				Assertions.assertThat(get("http://127.0.0.1:8080/get/hello?message=world")).isEqualTo("GET hello:world\n");
			}
		}
	}
}
