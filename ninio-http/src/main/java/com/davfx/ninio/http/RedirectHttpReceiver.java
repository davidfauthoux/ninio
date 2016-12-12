package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RedirectHttpReceiver implements HttpReceiver {
	private static final Logger LOGGER = LoggerFactory.getLogger(RedirectHttpReceiver.class);

	private final HttpClient client;
	
	private final int maxRedirections;
	private final int levelOfRedirect;
	private final HttpRequest request;
	private final HttpReceiver wrappee;
	
	public RedirectHttpReceiver(HttpClient client, int maxRedirections, HttpRequest request, HttpReceiver wrappee) {
		this(client, maxRedirections, 0, request, wrappee);
	}
	
	private RedirectHttpReceiver(HttpClient client, int maxRedirections, int levelOfRedirect, HttpRequest request, HttpReceiver wrappee) {
		if (client == null) {
			throw new NullPointerException("client");
		}
		if (wrappee == null) {
			throw new NullPointerException("wrappee");
		}
		
		this.client = client;
		this.maxRedirections = maxRedirections;
		this.levelOfRedirect = levelOfRedirect;
		this.request = request;
		this.wrappee = wrappee;
	}

	@Override
	public HttpContentReceiver received(final HttpResponse response) {
		if (levelOfRedirect < maxRedirections) {
			String location = null;
			for (String locationValue : response.headers.get(HttpHeaderKey.LOCATION)) {
				location = locationValue;
				break;
			}
			if (location != null) {
				String newHost;
				int newPort;
				boolean newSecure;
				String newPath;

				String l;
				int defaultPort = HttpSpecification.DEFAULT_PORT;
				if (location.startsWith(HttpSpecification.SECURE_PROTOCOL)) {
					l = location.substring(HttpSpecification.SECURE_PROTOCOL.length());
					newSecure = true;
					defaultPort = HttpSpecification.DEFAULT_SECURE_PORT;
				} else if (location.startsWith(HttpSpecification.PROTOCOL)) {
					l = location.substring(HttpSpecification.PROTOCOL.length());
					newSecure = false;
				} else {
					l = null;
					newSecure = request.address.secure;
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
						newHost = l;
						newPort = defaultPort;
					} else {
						int p;
						String portValue = l.substring(j + 1);
						if (portValue.isEmpty()) {
							p = defaultPort;
						} else {
							try {
								p = Integer.parseInt(portValue);
							} catch (NumberFormatException e) {
								LOGGER.debug("Bad location: {}", location);
								wrappee.received(response);
								return new IgnoreContentHttpContentReceiver();
							}
						}
						newHost = l.substring(0, j);
						newPort = p;
					}
					
				} else {
					newHost = request.address.host;
					newPort = request.address.port;
					newPath = location;
				}
				
				HttpRequest newRequest = new HttpRequest(new HttpRequestAddress(newHost, newPort, newSecure), request.method, newPath);
				
				client.request().build(newRequest).receive(new RedirectHttpReceiver(client, maxRedirections, levelOfRedirect + 1, newRequest, wrappee)).finish();

				return new IgnoreContentHttpContentReceiver();
			}
		}
		
		return wrappee.received(response);
	}
	
	private static final class IgnoreContentHttpContentReceiver implements HttpContentReceiver {
		public IgnoreContentHttpContentReceiver() {
		}
		@Override
		public void ended() {
		}
		@Override
		public void received(ByteBuffer buffer) {
		}
	}
	
	@Override
	public void failed(IOException ioe) {
		wrappee.failed(ioe);
	}
}
