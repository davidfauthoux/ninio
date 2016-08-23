package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.dns.DnsConnecter;
import com.davfx.ninio.dns.DnsReceiver;

final class RedirectHttpReceiver implements HttpReceiver {
	private static final Logger LOGGER = LoggerFactory.getLogger(RedirectHttpReceiver.class);

	private final DnsConnecter dns;
	private final HttpClient client;
	
	private final int maxRedirections;
	private final int levelOfRedirect;
	private final HttpRequest request;
	private final HttpReceiver wrappee;
	
	public RedirectHttpReceiver(DnsConnecter dns, HttpClient client, int maxRedirections, HttpRequest request, HttpReceiver wrappee) {
		this(dns, client, maxRedirections, 0, request, wrappee);
	}
	
	private RedirectHttpReceiver(DnsConnecter dns, HttpClient client, int maxRedirections, int levelOfRedirect, HttpRequest request, HttpReceiver wrappee) {
		if (dns == null) {
			throw new NullPointerException("dns");
		}
		if (client == null) {
			throw new NullPointerException("client");
		}
		if (wrappee == null) {
			throw new NullPointerException("wrappee");
		}
		
		this.dns = dns;
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
				//%% Address newAddress = request.address;
				final String newPath;
				String l = null;
				final boolean secure;
				int defaultPort = HttpSpecification.DEFAULT_PORT;
				if (location.startsWith(HttpSpecification.SECURE_PROTOCOL)) {
					l = location.substring(HttpSpecification.SECURE_PROTOCOL.length());
					secure = true;
					defaultPort = HttpSpecification.DEFAULT_SECURE_PORT;
				} else if (location.startsWith(HttpSpecification.PROTOCOL)) {
					l = location.substring(HttpSpecification.PROTOCOL.length());
					secure = false;
				} else {
					secure = false;
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
					final String newHost;
					final int newPort;
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
					
					dns.resolve(newHost, new DnsReceiver() {
						@Override
						public void failed(IOException e) {
							LOGGER.debug("Unable to resolve: {}", newHost);
							wrappee.received(response);
						}
						
						@Override
						public void received(byte[] ip) {
							HttpRequest newRequest = new HttpRequest(new Address(ip, newPort), secure, request.method, newPath);
							
							HttpRequestBuilder b = client.request();
							b.receive(new RedirectHttpReceiver(dns, client, maxRedirections, levelOfRedirect + 1, newRequest, wrappee));
							b.build(newRequest).finish();
						}
					});
					
					return new IgnoreContentHttpContentReceiver();

				} else {
					newPath = location;
					
					HttpRequest newRequest = new HttpRequest(request.address, secure, request.method, newPath);
					
					HttpRequestBuilder b = client.request();
					b.receive(new RedirectHttpReceiver(dns, client, maxRedirections, levelOfRedirect + 1, newRequest, wrappee));
					b.build(newRequest).finish();
					
					return new IgnoreContentHttpContentReceiver();
				}
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
