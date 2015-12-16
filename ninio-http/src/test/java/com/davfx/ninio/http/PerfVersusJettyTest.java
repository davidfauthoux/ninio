package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.assertj.core.api.Assertions;
import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.http.util.AnnotatedHttpService;
import com.davfx.ninio.http.util.HttpController;
import com.davfx.ninio.http.util.annotations.QueryParameter;
import com.davfx.ninio.http.util.annotations.Route;
import com.davfx.util.Lock;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMultimap;

@Ignore
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PerfVersusJettyTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PerfVersusJettyTest.class);
	
	static {
		// System.setProperty("http.keepAlive", "false");
		// System.setProperty("ninio.http.recyclers.ttl", "0");
		System.setProperty("ninio.http.gzip.default", "false");
	}
	
	private static final class JettyHandler extends AbstractHandler {
		@Override
		public void handle(String target, Request r, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
			response.setContentType("text/html;charset=utf-8");
			response.setStatus(HttpServletResponse.SC_OK);
			r.setHandled(true);
			response.getWriter().print("<html><body>" + request.getParameter("message") + "</body></html>");
		}
	}

	public static final class TestGetWithQueryParameterController implements HttpController {
		@Route(method = HttpMethod.GET)
		public Http echo(@QueryParameter("message") String message) {
			return Http.ok().contentType(HttpContentType.html()).content("<html><body>" + message + "</body></html>");
		}
	}
	
	private static final String PATH = "/?message=helloworld";
	private static final String EXPECTED_RESPONSE = "<html><body>helloworld_";

	private static long PREVENT_CACHE = 0L;
	private static String path() {
		PREVENT_CACHE++;
		return PATH + "_" + PREVENT_CACHE;
	}
	
	/*
	private static String getJava(int port) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL("http://localhost:" + port + path()).openConnection();
		StringBuilder b = new StringBuilder();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), Charsets.UTF_8))) {
			while (true) {
				String line = r.readLine();
				if (line == null) {
					break;
				}
				b.append(line);
			}
		}
		c.disconnect();
		return b.toString();
	}
	*/
	
	private static final Http http = new Http();
	private static String getNinio(int port) throws Exception {
		final Lock<String, IOException> lock = new Lock<>();
		http.send(new HttpRequest(new Address(Address.LOCALHOST, port), false, HttpMethod.GET, HttpPath.of(path())), null, new Http.InMemoryHandler() {
			@Override
			public void failed(IOException e) {
				lock.fail(e);
			}
			@Override
			public void handle(HttpResponse response, InMemoryBuffers content) {
				String s = content.toString();
				lock.set(s);
			}
		});
		String s = lock.waitFor();
		return s;
	}
	private static final HttpClient httpClient = new HttpClient();
	static {
		try {
			httpClient.start();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	private static String getJetty(int port) throws Exception {
		final Lock<String, IOException> lock = new Lock<>();
		ContentExchange c = new ContentExchange() {
			@Override
			protected void onResponseComplete() throws IOException {
				super.onResponseComplete();
				lock.set(getResponseContent());
			}
		};
		c.setURL("http://localhost:" + port + path());
		httpClient.send(c);
		c.waitForDone();
		String s = lock.waitFor();
		return s;
	}
	
	private static final int WARM_UP = 100;
	private static final int TOTAL = 10000;
	
	private static final int PORT = 8888;
	
	private static final class NinioServer1 implements AutoCloseable {
		private final Queue queue = new Queue();
		private final HttpServer server;
		public NinioServer1() {
			server = new HttpServer(queue, null, new Address(Address.ANY, PORT), new HttpServerHandlerFactory() {
				@Override
				public void failed(IOException e) {
					LOGGER.error("Failed", e);
				}
				@Override
				public void closed() {
				}
				
				@Override
				public HttpServerHandler create() {
					return new HttpServerHandler() {
						private HttpRequest request;
						private final InMemoryBuffers b = new InMemoryBuffers();
						
						@Override
						public void failed(IOException e) {
							LOGGER.warn("Failed", e);
						}
						@Override
						public void close() {
							LOGGER.debug("Closed");
						}
						
						@Override
						public void handle(HttpRequest request) {
							this.request = request;
						}
	
						@Override
						public void handle(Address address, ByteBuffer buffer) {
							b.add(buffer);
						}
						
						@Override
						public void ready(Write write) {
							String message = request.path.parameters.get("message").iterator().next().get();
							ByteBuffer m = ByteBuffer.wrap(("<html><body>" + message + "</body></html>").getBytes(Charsets.UTF_8));
							write.write(new HttpResponse(HttpStatus.OK, HttpMessage.OK, ImmutableMultimap.of(
									HttpHeaderKey.CONTENT_TYPE, HttpContentType.html(),
									HttpHeaderKey.CONTENT_LENGTH, String.valueOf(m.remaining())
								)));
							write.handle(null, m);
							write.close();
						}
						
					};
				}
			});
			queue.finish().waitFor();
		}
		
		@Override
		public void close() {
			server.close();
			queue.finish().waitFor();
			queue.close();
		}
	}
	
	private static final class NinioServer2 implements AutoCloseable {
		private final Queue queue = new Queue();
		private final AnnotatedHttpService server;
		public NinioServer2() {
			server = new AnnotatedHttpService(queue, new Address(Address.ANY, PORT));
			server.register(HttpQueryPath.of(), new TestGetWithQueryParameterController());
			queue.finish().waitFor();
		}
		
		@Override
		public void close() {
			server.close();
			queue.finish().waitFor();
			queue.close();
		}
	}

	@Test
	public void test1_JettyWithNinio() throws Exception {
		Server server = new Server(PORT);
		try {
			server.setHandler(new JettyHandler());
	
			server.start();
			LOGGER.debug("Jetty pool: {}", server.getThreadPool().getThreads());
			
			for (int i = 0; i < WARM_UP; i++) {
				Assertions.assertThat(getNinio(PORT)).startsWith(EXPECTED_RESPONSE);
			}
			LOGGER.info("Up");
			long t = System.nanoTime();
			for (int i = 0; i < TOTAL; i++) {
				Assertions.assertThat(getNinio(PORT)).startsWith(EXPECTED_RESPONSE);
			}
			t = System.nanoTime() - t;
			double p = t / 1000000000d;
			LOGGER.info("test_JettyWithNinio: Time for {} requests = {} seconds", TOTAL, p);
		} finally {
			server.stop();
		}
		Thread.sleep(1000);
	}

	@Test
	public void test1_Ninio1WithNinio() throws Exception {
		try (NinioServer1 server = new NinioServer1()) {
			for (int i = 0; i < WARM_UP; i++) {
				Assertions.assertThat(getNinio(PORT)).startsWith(EXPECTED_RESPONSE);
			}
			LOGGER.info("Up");
			long t = System.nanoTime();
			for (int i = 0; i < TOTAL; i++) {
				Assertions.assertThat(getNinio(PORT)).startsWith(EXPECTED_RESPONSE);
			}
			t = System.nanoTime() - t;
			double p = t / 1000000000d;
			LOGGER.info("test_Ninio1WithNinio: Time for {} requests = {} seconds", TOTAL, p);
		}
		Thread.sleep(1000);
	}
	
	@Test
	public void test1_Ninio2WithNinio() throws Exception {
		try (NinioServer2 server = new NinioServer2()) {
			for (int i = 0; i < WARM_UP; i++) {
				Assertions.assertThat(getNinio(PORT)).startsWith(EXPECTED_RESPONSE);
			}
			LOGGER.info("Up");
			long t = System.nanoTime();
			for (int i = 0; i < TOTAL; i++) {
				Assertions.assertThat(getNinio(PORT)).startsWith(EXPECTED_RESPONSE);
			}
			t = System.nanoTime() - t;
			double p = t / 1000000000d;
			LOGGER.info("test_Ninio2WithNinio: Time for {} requests = {} seconds", TOTAL, p);
		}
		Thread.sleep(1000);
	}
	
	@Test
	public void test1_JettyWithJetty() throws Exception {
		Server server = new Server(PORT);
		try {
			server.setHandler(new JettyHandler());
	
			server.start();
			LOGGER.debug("Jetty pool: {}", server.getThreadPool().getThreads());
			
			for (int i = 0; i < WARM_UP; i++) {
				Assertions.assertThat(getJetty(PORT)).startsWith(EXPECTED_RESPONSE);
			}
			LOGGER.info("Up");
			long t = System.nanoTime();
			for (int i = 0; i < TOTAL; i++) {
				Assertions.assertThat(getJetty(PORT)).startsWith(EXPECTED_RESPONSE);
			}
			t = System.nanoTime() - t;
			double p = t / 1000000000d;
			LOGGER.info("test_JettyWithJetty: Time for {} requests = {} seconds", TOTAL, p);
		} finally {
			server.stop();
		}
		Thread.sleep(1000);
	}

	@Test
	public void test1_Ninio1WithJetty() throws Exception {
		try (NinioServer1 server = new NinioServer1()) {
			for (int i = 0; i < WARM_UP; i++) {
				Assertions.assertThat(getJetty(PORT)).startsWith(EXPECTED_RESPONSE);
			}
			LOGGER.info("Up");
			long t = System.nanoTime();
			for (int i = 0; i < TOTAL; i++) {
				Assertions.assertThat(getJetty(PORT)).startsWith(EXPECTED_RESPONSE);
			}
			t = System.nanoTime() - t;
			double p = t / 1000000000d;
			LOGGER.info("test_Ninio1WithJetty: Time for {} requests = {} seconds", TOTAL, p);
		}
		Thread.sleep(1000);
	}
	
	@Test
	public void test1_Ninio2WithJetty() throws Exception {
		try (NinioServer2 server = new NinioServer2()) {
			for (int i = 0; i < WARM_UP; i++) {
				Assertions.assertThat(getJetty(PORT)).startsWith(EXPECTED_RESPONSE);
			}
			LOGGER.info("Up");
			long t = System.nanoTime();
			for (int i = 0; i < TOTAL; i++) {
				Assertions.assertThat(getJetty(PORT)).startsWith(EXPECTED_RESPONSE);
			}
			t = System.nanoTime() - t;
			double p = t / 1000000000d;
			LOGGER.info("test_Ninio2WithJetty: Time for {} requests = {} seconds", TOTAL, p);
		}
		Thread.sleep(1000);
	}
	
	@Test
	public void test2_JettyWithNinio() throws Exception {
		test1_JettyWithNinio();
	}

	@Test
	public void test2_Ninio1WithNinio() throws Exception {
		test1_Ninio1WithNinio();
	}
	
	@Test
	public void test2_Ninio2WithNinio() throws Exception {
		test1_Ninio2WithNinio();
	}
	
	@Test
	public void test2_JettyWithJetty() throws Exception {
		test1_JettyWithJetty();
	}

	@Test
	public void test2_Ninio1WithJetty() throws Exception {
		test1_Ninio1WithJetty();
	}

	@Test
	public void test2_Ninio2WithJetty() throws Exception {
		test1_Ninio2WithJetty();
	}
}
