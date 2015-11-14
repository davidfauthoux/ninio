package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.util.QueueScheduled;
import com.davfx.util.ConfigUtils;
import com.davfx.util.DateUtils;
import com.google.common.base.Charsets;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class HttpClient implements AutoCloseable, Closeable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpClient.Recycler.class);
	
	private static final Config CONFIG = ConfigFactory.load(HttpClient.class.getClassLoader());

	private static final int MAX_REDIRECT_LEVELS = CONFIG.getInt("ninio.http.redirect.max");
	private static final double RECYCLERS_TIME_TO_LIVE = ConfigUtils.getDuration(CONFIG, "ninio.http.recyclers.ttl");
	private static final double RECYCLERS_CHECK_TIME = ConfigUtils.getDuration(CONFIG, "ninio.http.recyclers.check");

	private static final class Recycler {
		public HttpResponseReader reader;
		public HttpClientHandler handler;
		public CloseableByteBufferHandler write;
		public double closeDate = 0d;
		public boolean closed = false;
	}

	private final Queue queue;

	private final ReadyFactory readyFactory;
	private final ReadyFactory secureReadyFactory;

	private final Map<Address, Deque<Recycler>> recyclers = new HashMap<Address, Deque<Recycler>>();
	private final Closeable closeable;

	public HttpClient(Queue queue, ReadyFactory readyFactory, ReadyFactory secureReadyFactory) {
		this.queue = queue;
		this.readyFactory = readyFactory;
		this.secureReadyFactory = secureReadyFactory;
		
		closeable = QueueScheduled.schedule(queue, RECYCLERS_CHECK_TIME, new Runnable() {
			@Override
			public void run() {
				double now = DateUtils.now();
				Iterator<Deque<Recycler>> recyclersIterator = recyclers.values().iterator();
				while (recyclersIterator.hasNext()) {
					Deque<Recycler> l = recyclersIterator.next();
					Iterator<Recycler> i = l.iterator();
					while (i.hasNext()) {
						Recycler r = i.next();
						if ((r.closeDate > 0d) && (r.closeDate <= now)) {
							r.write.close();
							i.remove();
						}
					}
					if (l.isEmpty()) {
						recyclersIterator.remove();
					}
				}
			}
		});
	}
	
	@Override
	public void close() {
		closeable.close();
	}
	
	public void send(final HttpRequest request, final HttpClientHandler clientHandler) {
		final HttpClientHandler handler = new RedirectHandler(0, this, request, clientHandler);
		queue.post(new Runnable() {
			@Override
			public void run() {
				final HttpResponseReader reader = new HttpResponseReader(handler);
				if (request.method != HttpMethod.GET) {
					Ready ready;
					if (request.secure) {
						ready = secureReadyFactory.create(queue);
					} else {
						ready = readyFactory.create(queue);
					}
					ready.connect(request.address, new ReadyConnection() {
						@Override
						public void handle(Address address, ByteBuffer buffer) {
							reader.handle(buffer, null);
						}
						
						@Override
						public void failed(IOException e) {
							reader.failed(e);
						}
						
						@Override
						public void connected(final FailableCloseableByteBufferHandler write) {
							write.handle(null, createRequest(request));
							handler.ready(write);
						}
						
						@Override
						public void close() {
							reader.close();
						}
					});
					return;
				}
				
				Deque<Recycler> oldRecyclers = recyclers.get(request.address);

				if (oldRecyclers != null) {
					while (!oldRecyclers.isEmpty()) {
						LOGGER.trace("Recycling connection to {}", request.address);
						Recycler oldRecycler = oldRecyclers.removeFirst();
						if (oldRecyclers.isEmpty()) {
							recyclers.remove(request.address);
						}
						if (!oldRecycler.closed) {
							oldRecycler.reader = reader;
							oldRecycler.handler = handler;
							oldRecycler.closeDate = 0d;
							oldRecycler.write.handle(null, createRequest(request));
							oldRecycler.handler.ready(oldRecycler.write);
							return;
						}
					}
				}

				final Recycler newRecycler = new Recycler();
				newRecycler.reader = reader;
				newRecycler.handler = handler;
				newRecycler.closeDate = 0d;
				Ready ready;
				if (request.secure) {
					ready = secureReadyFactory.create(queue);
				} else {
					ready = readyFactory.create(queue);
				}
				ready.connect(request.address, new ReadyConnection() {
					private HttpResponseReader.RecyclingHandler recyclingHandler;
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						if (newRecycler.reader == null) {
							return;
						}
						
						newRecycler.reader.handle(buffer, recyclingHandler);
					}
					
					@Override
					public void failed(IOException e) {
						newRecycler.closed = true;
						if (newRecycler.reader == null) {
							return;
						}
						newRecycler.reader.failed(e);
					}
					
					@Override
					public void connected(final FailableCloseableByteBufferHandler write) {
						if (newRecycler.handler == null) {
							return;
						}
						
						recyclingHandler = new HttpResponseReader.RecyclingHandler() {
							@Override
							public void recycle() {
								newRecycler.reader = null;
								newRecycler.handler = null;
								newRecycler.closeDate = DateUtils.now() + RECYCLERS_TIME_TO_LIVE;
								Deque<Recycler> oldRecyclers = recyclers.get(request.address);
								if (oldRecyclers == null) {
									oldRecyclers = new LinkedList<Recycler>();
									recyclers.put(request.address, oldRecyclers);
								}
								oldRecyclers.add(newRecycler);
							}
							@Override
							public void close() {
								write.close();
							}
						};

						newRecycler.write = new CloseableByteBufferHandler() {
							@Override
							public void handle(Address address, ByteBuffer buffer) {
								write.handle(address, buffer);
							}
							@Override
							public void close() {
								write.close();
								newRecycler.closed = true;
								if (newRecycler.reader == null) {
									return;
								}
								newRecycler.reader.close();
							}
						};
						write.handle(null, createRequest(request));
						newRecycler.handler.ready(newRecycler.write);
					}
					
					@Override
					public void close() {
						newRecycler.closed = true;
						if (newRecycler.reader == null) {
							return;
						}
						newRecycler.reader.close();
					}
				});
			}
		});
	}
	
	private static void appendHeader(StringBuilder buffer, String key, String value) {
		buffer.append(key).append(HttpSpecification.HEADER_KEY_VALUE_SEPARATOR).append(HttpSpecification.HEADER_BEFORE_VALUE).append(value).append(HttpSpecification.CR).append(HttpSpecification.LF);
	}
	private static ByteBuffer createRequest(HttpRequest request) {
		StringBuilder header = new StringBuilder();
		header.append(request.method.toString()).append(HttpSpecification.START_LINE_SEPARATOR).append(request.path).append(HttpSpecification.START_LINE_SEPARATOR).append(HttpSpecification.HTTP11).append(HttpSpecification.CR).append(HttpSpecification.LF);
		
		for (Map.Entry<String, String> h : request.headers.entries()) {
			appendHeader(header, h.getKey(), h.getValue());
		}
		if (!request.headers.containsKey(HttpHeaderKey.HOST)) {
			appendHeader(header, HttpHeaderKey.HOST, request.address.getHost()); //TODO check that! // Adding the port looks to fail with Apache/Coyote // + Http.PORT_SEPARATOR + request.getAddress().getPort());
		}
		if (!request.headers.containsKey(HttpHeaderKey.ACCEPT_ENCODING)) {
			appendHeader(header, HttpHeaderKey.ACCEPT_ENCODING, HttpHeaderValue.GZIP);
		}
		if (!request.headers.containsKey(HttpHeaderKey.CONNECTION)) {
			appendHeader(header, HttpHeaderKey.CONNECTION, HttpHeaderValue.KEEP_ALIVE);
		}
		if (!request.headers.containsKey(HttpHeaderKey.USER_AGENT)) {
			appendHeader(header, HttpHeaderKey.USER_AGENT, HttpHeaderValue.DEFAULT_USER_AGENT);
		}
		if (!request.headers.containsKey(HttpHeaderKey.ACCEPT)) {
			appendHeader(header, HttpHeaderKey.ACCEPT, HttpHeaderValue.DEFAULT_ACCEPT);
		}
		
		header.append(HttpSpecification.CR).append(HttpSpecification.LF);
		LOGGER.trace("Header: {}", header);
		return ByteBuffer.wrap(header.toString().getBytes(Charsets.US_ASCII));
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
						
						HttpRequest newRequest = new HttpRequest(newAddress, secure, request.method, new HttpPath(newPath));
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
