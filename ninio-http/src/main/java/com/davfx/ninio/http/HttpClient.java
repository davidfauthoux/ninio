package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class HttpClient {
	
	private static final Config CONFIG = ConfigFactory.load(HttpClient.class.getClassLoader());

	private static final int MAX_REDIRECT_LEVELS = CONFIG.getInt("ninio.http.redirect.max");

	private final Queue queue;
	private final HttpRecycle recycle;

	public HttpClient(Queue queue, HttpRecycle recycle) {
		this.queue = queue;
		this.recycle = recycle;
	}
	
	public void send(final HttpRequest request, final HttpClientHandler clientHandler) {
		final HttpClientHandler handler = new RedirectHandler(0, this, request, clientHandler);
		queue.post(new Runnable() {
			@Override
			public void run() {
				recycle.connect(request, handler);
			}
		});
	}
	
	private static class RedirectHandler implements HttpClientHandler {
		private final int levelOfRedirect;
		private final HttpClient client;
		private final HttpRequest request;
		private final HttpClientHandler wrappee;
		private boolean redirected = false;
		
		public RedirectHandler(int levelOfRedirect, HttpClient client, HttpRequest request, HttpClientHandler wrappee) {
			this.levelOfRedirect = levelOfRedirect;
			this.client = client;
			this.request = request;
			this.wrappee = wrappee;
		}
		
		@Override
		public void failed(IOException e) {
			if (redirected) {
				return;
			}
			wrappee.failed(e);
		}
		@Override
		public void close() {
			if (redirected) {
				return;
			}
			wrappee.close();
		}
		
		@Override
		public void received(HttpResponse response) {
			try {
				if (levelOfRedirect < MAX_REDIRECT_LEVELS) {
					String location = null;
					for (String locationValue : response.headers.get(HttpHeaderKey.LOCATION)) {
						location = locationValue;
						break;
					}
					if (location != null) {
						Address newAddress = request.address;
						String newPath;
						String l = null;
						boolean secure = false;
						int defaultPort = HttpSpecification.DEFAULT_PORT;
						if (location.startsWith(HttpSpecification.SECURE_PROTOCOL)) {
							l = location.substring(HttpSpecification.SECURE_PROTOCOL.length());
							secure = true;
							defaultPort = HttpSpecification.DEFAULT_SECURE_PORT;
						} else if (location.startsWith(HttpSpecification.PROTOCOL)) {
							l = location.substring(HttpSpecification.PROTOCOL.length());
						}
						if (l != null) {
							int i = l.indexOf(HttpSpecification.PATH_SEPARATOR);
							if (i > 0) {
								newPath = l.substring(i);
								l = l.substring(0, i);
							} else {
								newPath = String.valueOf(HttpSpecification.PATH_SEPARATOR);
							}
							int j = l.indexOf(HttpSpecification.PORT_SEPARATOR);
							if (j < 0) {
								newAddress = new Address(l, defaultPort);
							} else {
								int newPort;
								String portValue = l.substring(j + 1);
								if (portValue.isEmpty()) {
									newPort = defaultPort;
								} else {
									try {
										newPort = Integer.parseInt(portValue);
									} catch (NumberFormatException e) {
										throw new IOException("Bad location: " + location);
									}
								}
								newAddress = new Address(l.substring(0, j), newPort);
							}
						} else {
							newPath = location;
						}
						
						redirected = true;
						
						HttpRequest newRequest = new HttpRequest(newAddress, secure, request.method, HttpPath.of(newPath));
						client.send(newRequest, new RedirectHandler(levelOfRedirect + 1, client, newRequest, wrappee));
						return;
					}
				}
				wrappee.received(response);
			} catch (IOException e) {
				wrappee.failed(e);
			}
		}
		
		@Override
		public void handle(Address address, ByteBuffer buffer) {
			if (redirected) {
				return;
			}
			wrappee.handle(address, buffer);
		}
		
		@Override
		public void ready(CloseableByteBufferHandler write) {
			if (redirected) {
				return;
			}
			wrappee.ready(write);
		}
	}

}
