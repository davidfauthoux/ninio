package com.davfx.ninio.http.v3;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.http.HttpHeaderKey;
import com.davfx.ninio.http.HttpPath;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpSpecification;

final class RedirectHttpReceiver implements HttpReceiver {
	private static final Logger LOGGER = LoggerFactory.getLogger(RedirectHttpReceiver.class);

	private final int maxRedirections;
	private final int levelOfRedirect;
	private final HttpRequest request;
	private final HttpReceiverRequestBuilder client;
	private final HttpReceiver wrappee;
	private boolean redirected = false;

	public RedirectHttpReceiver(int maxRedirections, HttpRequest request, HttpReceiverRequestBuilder client, HttpReceiver wrappee) {
		this(maxRedirections, 0, request, client, wrappee);
	}
	
	private RedirectHttpReceiver(int maxRedirections, int levelOfRedirect, HttpRequest request, HttpReceiverRequestBuilder client, HttpReceiver wrappee) {
		this.maxRedirections = maxRedirections;
		this.levelOfRedirect = levelOfRedirect;
		this.request = request;
		this.client = client;
		this.wrappee = wrappee;
	}

	@Override
	public void received(HttpResponse response) {
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
								wrappee.received(response);
								return;
							}
						}
						newAddress = new Address(l.substring(0, j), newPort);
					}
				} else {
					newPath = location;
				}
				
				redirected = true;
				
				HttpRequest newRequest = new HttpRequest(newAddress, secure, request.method, HttpPath.of(newPath));
				client.receiving(new RedirectHttpReceiver(maxRedirections, levelOfRedirect + 1, newRequest, client, wrappee)).build().create(newRequest).finish();
				return;
			}
		}
		
		wrappee.received(response);
	}
	
	@Override
	public void received(ByteBuffer buffer) {
		if (!redirected) {
			wrappee.received(buffer);
		}
	}
	
	@Override
	public void ended() {
		if (!redirected) {
			wrappee.ended();
			redirected = false;
		}
	}
}
