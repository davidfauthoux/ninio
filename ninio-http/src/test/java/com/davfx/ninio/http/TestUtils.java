package com.davfx.ninio.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connected;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Listener;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.RoutingTcpSocketServer;
import com.davfx.ninio.core.TcpSocket;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.http.service.Annotated;
import com.davfx.ninio.http.service.Annotated.Builder;
import com.davfx.ninio.http.service.HttpController;
import com.davfx.ninio.http.service.HttpService;
import com.davfx.ninio.util.SerialExecutor;
import com.davfx.ninio.util.Wait;
import com.google.common.base.Charsets;
import com.google.common.base.Supplier;

class TestUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestUtils.class);
	
	static {
		System.setProperty("http.keepAlive", "false");
	}
	
	static interface Visitor {
		void visit(Annotated.Builder builder);
	}
	
	static final class ControllerVisitor implements Visitor {
		private final Class<? extends HttpController> clazz;
		public ControllerVisitor(Class<? extends HttpController> clazz) {
			this.clazz = clazz;
		}
		@Override
		public void visit(Builder builder) {
			builder.register(clazz);
		}
	}
	static final class InterceptorControllerVisitor implements Visitor {
		private final Class<? extends HttpController> iclazz;
		private final Class<? extends HttpController> clazz;
		public InterceptorControllerVisitor(Class<? extends HttpController> iclazz, Class<? extends HttpController> clazz) {
			this.iclazz = iclazz;
			this.clazz = clazz;
		}
		@Override
		public void visit(Builder builder) {
			builder.intercept(iclazz);
			builder.register(clazz);
		}
	}
	
	static Disconnectable server(Ninio ninio, int port, Visitor v) {
		LOGGER.debug("CREATING ON PORT: {}", port);
		final Annotated.Builder a = Annotated.builder(HttpService.builder());
		v.visit(a);

		final Wait wait = new Wait();
		final Wait waitForClosing = new Wait();
		final Listener tcp = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)));
		tcp.listen(new Listener.Callback() {
			@Override
			public void closed() {
				waitForClosing.run();
			}
			
			@Override
			public void failed(IOException e) {
			}
			
			@Override
			public void connected(Address address) {
				wait.run();
			}
			
			@Override
			public Connection connecting(Connected connecting) {
				return HttpListening.builder().with(new SerialExecutor(HttpServiceSimpleTest.class)).with(a.build()).build().connecting(connecting);
			}
		});
		wait.waitFor();
		return new Disconnectable() {
			@Override
			public void close() {
				tcp.close();
				waitForClosing.waitFor();
			}
		};
	}

	static Disconnectable routedServer(Ninio ninio, int routedPort, final int port, Visitor v) {
		LOGGER.debug("CREATING ROUTED ON PORTS: {} -> {}", routedPort, port);
		final Annotated.Builder a = Annotated.builder(HttpService.builder());
		v.visit(a);

		final Wait wait = new Wait();
		final Wait waitForClosing = new Wait();
		final Listener tcp = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)));
		tcp.listen(new Listener.Callback() {
			@Override
			public void closed() {
				waitForClosing.run();
			}
			
			@Override
			public void failed(IOException e) {
			}
			
			@Override
			public void connected(Address address) {
				wait.run();
			}
			
			@Override
			public Connection connecting(Connected connecting) {
				return HttpListening.builder().with(new SerialExecutor(HttpServiceSimpleTest.class)).with(a.build()).build().connecting(connecting);
			}
		});
		wait.waitFor();

		final Wait routedWait = new Wait();
		final Wait routedWaitForClosing = new Wait();
		final Listener routedTcp = ninio.create(/*TODO TcpSocketServer.builder().bind(new Address(Address.ANY, routedPort)));*/
			RoutingTcpSocketServer.builder().serve(TcpSocketServer.builder().bind(new Address(Address.ANY, routedPort)))
				.to(new Supplier<TcpSocket.Builder>() {
					@Override
					public TcpSocket.Builder get() {
						return TcpSocket.builder().to(new Address(Address.LOCALHOST, port));
					}
				})
		);
		routedTcp.listen(new Listener.Callback() {
			@Override
			public void closed() {
				routedWaitForClosing.run();
			}
			
			@Override
			public void failed(IOException e) {
			}
			
			@Override
			public void connected(Address address) {
				routedWait.run();
			}
			
			@Override
			public Connection connecting(Connected connecting) {
				return HttpListening.builder().with(new SerialExecutor(HttpServiceSimpleTest.class)).with(a.build()).build().connecting(connecting);
			}
		});
		routedWait.waitFor();

		return new Disconnectable() {
			@Override
			public void close() {
				routedTcp.close();	
				tcp.close();
				waitForClosing.waitFor();
				routedWaitForClosing.waitFor();
			}
		};
	}

	static String get(String url) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		LOGGER.debug("Request headers: {}", c.getRequestProperties());
		if (c.getResponseCode() != 200) {
			throw new IOException("Response error: " + c.getResponseCode());
		}
		StringBuilder b = new StringBuilder();
		b.append(c.getHeaderField("Content-Type"));
		b.append("/");
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

	static String post(String url, String post) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		LOGGER.debug("Request headers: {}", c.getRequestProperties());
		c.setDoOutput(true);
		try (Writer w = new OutputStreamWriter(c.getOutputStream())) {
			w.write(post);
		}
		if (c.getResponseCode() != 200) {
			throw new IOException("Response error: " + c.getResponseCode());
		}
		StringBuilder b = new StringBuilder();
		b.append(c.getHeaderField("Content-Type"));
		b.append("/");
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
}
