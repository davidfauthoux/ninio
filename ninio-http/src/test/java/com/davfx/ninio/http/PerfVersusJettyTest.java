package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

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
import com.davfx.ninio.core.InMemoryBuffers;
import com.davfx.ninio.core.Limit;
import com.davfx.ninio.core.Listener;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.Nop;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.core.Timeout;
import com.davfx.ninio.http.service.Annotated;
import com.davfx.ninio.http.service.HttpContentType;
import com.davfx.ninio.http.service.HttpController;
import com.davfx.ninio.http.service.HttpService;
import com.davfx.ninio.http.service.annotations.QueryParameter;
import com.davfx.ninio.http.service.annotations.Route;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.SerialExecutor;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMultimap;

@Ignore
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PerfVersusJettyTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PerfVersusJettyTest.class);
	
	static {
		// System.setProperty("http.keepAlive", "false");
		//TODO Check it does not use gzip encoding
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
			String c = "<html><body>" + message + "</body></html>";
			return Http.ok().contentType(HttpContentType.html()).contentLength(c.length() /* assumed utf-8 */).content(c);
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

	private static final Ninio ninio = Ninio.create();
	private static final com.davfx.ninio.http.HttpConnecter ninioClient;
	private static final Timeout timeout = new Timeout();
	private static final Executor executor = new SerialExecutor(HttpGetTest.class);
	private static final Limit limit = new Limit(10);
	static {
		ninioClient = ninio.create(com.davfx.ninio.http.HttpClient.builder().with(executor));
	}

	private static String getNinio(int port) throws Exception {
		final Lock<String, IOException> lock = new Lock<>();
		HttpRequestBuilder b = HttpTimeout.wrap(timeout, 1d, HttpLimit.wrap(limit, ninioClient.request()));
		HttpContentSender s = b.build(HttpRequest.of("http://127.0.0.1:" + port + path(), HttpMethod.GET, ImmutableMultimap.<String, String>of(HttpHeaderKey.ACCEPT_ENCODING, HttpHeaderValue.IDENTITY, HttpHeaderKey.CONTENT_ENCODING, HttpHeaderValue.IDENTITY)));
		b.receive(new HttpReceiver() {
			@Override
			public HttpContentReceiver received(HttpResponse response) {
				return new HttpContentReceiver() {
					private final InMemoryBuffers b = new InMemoryBuffers();
					@Override
					public void received(ByteBuffer buffer) {
						LOGGER.debug("-----------------> {}", new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining()));
						b.add(buffer);
					}
					@Override
					public void ended() {
						LOGGER.debug("-----------------> END {}", b.toString());
						lock.set(b.toString());
					}
				};
			}
			
			@Override
			public void failed(IOException ioe) {
				lock.fail(ioe);
			}
		});
		s.finish();
		return lock.waitFor();
	}
	private static final HttpClient jettyClient = new HttpClient();
	static {
		try {
			jettyClient.start();
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
		jettyClient.send(c);
		c.waitForDone();
		String s = lock.waitFor();
		return s;
	}
	
	private static final int WARM_UP = 100;
	private static final int TOTAL = 10000;
	
	private static final int PORT = 8888;
	
	private static final class NinioServer1 implements AutoCloseable {
		private final Listener server;
		public NinioServer1() {
			server = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, PORT)));
			server.listen(HttpListening.builder().with(new SerialExecutor(NinioServer1.class)).with(new HttpListeningHandler() {
				@Override
				public HttpContentReceiver handle(final HttpRequest request, final HttpResponseSender responseHandler) {
					return new HttpContentReceiver() {
						@Override
						public void received(ByteBuffer buffer) {
						}
						@Override
						public void ended() {
							String message = HttpRequest.parameters(request.path).get("message").iterator().next().get();
							ByteBuffer m = ByteBuffer.wrap(("<html><body>" + message + "</body></html>").getBytes(Charsets.UTF_8));

							HttpContentSender sender = responseHandler.send(new HttpResponse(HttpStatus.OK, HttpMessage.OK, ImmutableMultimap.of(
									HttpHeaderKey.CONTENT_TYPE, HttpContentType.html(),
									HttpHeaderKey.CONTENT_LENGTH, String.valueOf(m.remaining())
								)));
							
							sender.send(m, new Nop());
							sender.finish();
						}
					};
				}
				@Override
				public void connected(Address address) {
				}
				@Override
				public void failed(IOException ioe) {
				}
				@Override
				public void closed() {
				}
			}).build());
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}
		}
		
		@Override
		public void close() {
			server.close();
		}
	}
	
	private static final class NinioServer2 implements AutoCloseable {
		private final Listener server;
		public NinioServer2() {
			Annotated.Builder a = Annotated.builder(HttpService.builder());
			a.register(new TestGetWithQueryParameterController());

			server = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, PORT)));
			server.listen(HttpListening.builder().with(new SerialExecutor(HttpServiceSimpleTest.class)).with(a.build()).build());
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}
		}
		
		@Override
		public void close() {
			server.close();
		}
	}

	@Test
	public void test1_JettyWithNinio() throws Exception {
		Server server = new Server(PORT);
		try {
			server.setHandler(new JettyHandler());
	
			server.start();
			LOGGER.info("Jetty pool: {}", server.getThreadPool().getThreads());
			
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
			LOGGER.info("Jetty pool: {}", server.getThreadPool().getThreads());
			
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
