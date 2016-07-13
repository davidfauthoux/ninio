package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;

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
	public HttpContentReceiver received(HttpResponse response) {
		if (levelOfRedirect < maxRedirections) {
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
								LOGGER.debug("Bad location: {}", location);
								return wrappee.received(response);
							}
						}
						newAddress = new Address(l.substring(0, j), newPort);
					}
				} else {
					newPath = location;
				}
				
				HttpRequest newRequest = new HttpRequest(newAddress, secure, request.method, newPath);
				
				client.request().build(newRequest).finish(new RedirectHttpReceiver(client, maxRedirections, levelOfRedirect + 1, newRequest, wrappee));
				
				return new HttpContentReceiver() {
					@Override
					public void received(ByteBuffer buffer) {
					}
					@Override
					public void ended() {
					}
				};
			}
		}
		
		return wrappee.received(response);
	}
	
	@Override
	public void failed(IOException ioe) {
		wrappee.failed(ioe);
	}
}
