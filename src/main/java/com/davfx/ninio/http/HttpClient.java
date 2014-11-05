package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.ByteBufferHandler;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.common.SocketReadyFactory;
import com.davfx.ninio.common.SslReadyFactory;
import com.davfx.ninio.common.Trust;
import com.davfx.util.ConfigUtils;
import com.davfx.util.DateUtils;
import com.typesafe.config.Config;

public final class HttpClient implements AutoCloseable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpClient.Recycler.class);
	
	private static final class Recycler {
		public HttpResponseReader reader;
		public HttpClientHandler handler;
		public CloseableByteBufferHandler write;
		public Date closeDate = null;
		public boolean closed = false;
	}

	private static final Config CONFIG = ConfigUtils.load(HttpClient.class);
	
	private final Map<Address, Deque<Recycler>> recyclers = new HashMap<Address, Deque<Recycler>>();
	private final Queue queue;
	private ScheduledExecutorService recyclersCloserExecutorToClose;

	private final int defaultMaxRedirectLevels;
	private final double recyclersTimeToLive;

	private ReadyFactory readyFactory;
	private ReadyFactory secureReadyFactory;

	public HttpClient(Queue queue, Trust trust) {
		this(queue, trust, null);
	}
	public HttpClient(final Queue queue, Trust trust, ScheduledExecutorService recyclersCloserExecutor) {
		this.queue = queue;
		
		readyFactory = new SocketReadyFactory();
		secureReadyFactory = new SslReadyFactory(trust);
		
		defaultMaxRedirectLevels = CONFIG.getInt("http.redirect.max.default");
		recyclersTimeToLive = ConfigUtils.getDuration(CONFIG, "http.recyclers.ttl");
		
		if (recyclersCloserExecutor == null) {
			recyclersCloserExecutor = Executors.newSingleThreadScheduledExecutor();
			recyclersCloserExecutorToClose = recyclersCloserExecutor;
		} else {
			recyclersCloserExecutorToClose = null;
		}
		
		recyclersCloserExecutor.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				queue.post(new Runnable() {
					@Override
					public void run() {
						Date now = new Date();
						Iterator<Deque<Recycler>> recyclersIterator = recyclers.values().iterator();
						while (recyclersIterator.hasNext()) {
							Deque<Recycler> l = recyclersIterator.next();
							Iterator<Recycler> i = l.iterator();
							while (i.hasNext()) {
								Recycler r = i.next();
								if ((r.closeDate != null) && r.closeDate.before(now)) {
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
		}, 0, (long) (ConfigUtils.getDuration(CONFIG, "http.recyclers.check") * 1000d), TimeUnit.MILLISECONDS);
	}
	
	public HttpClient override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
	public HttpClient overrideSecure(ReadyFactory readyFactory) {
		secureReadyFactory = readyFactory;
		return this;
	}
	
	@Override
	public void close() {
		if (recyclersCloserExecutorToClose != null) {
			recyclersCloserExecutorToClose.shutdown();
		}
	}
	
	public void send(HttpRequest request, HttpClientHandler clientHandler) {
		send(request, defaultMaxRedirectLevels, clientHandler);
	}
	public void send(final HttpRequest request, int maxRedirectLevels, HttpClientHandler clientHandler) {
		final HttpClientHandler handler = new RedirectHandler(maxRedirectLevels, 0, this, request, clientHandler);
		queue.post(new Runnable() {
			@Override
			public void run() {
				HttpResponseReader reader = new HttpResponseReader(handler);
				Deque<Recycler> oldRecyclers = recyclers.get(request.getAddress());

				if (oldRecyclers != null) {
					while (!oldRecyclers.isEmpty()) {
						LOGGER.debug("Recycling connection to {}", request.getAddress());
						Recycler oldRecycler = oldRecyclers.removeFirst();
						if (oldRecyclers.isEmpty()) {
							recyclers.remove(request.getAddress());
						}
						if (!oldRecycler.closed) {
							oldRecycler.reader = reader;
							oldRecycler.handler = handler;
							oldRecycler.closeDate = null;
							oldRecycler.write.handle(null, createRequest(request));
							oldRecycler.handler.ready(oldRecycler.write);
							return;
						}
					}
				}

				final Recycler newRecycler = new Recycler();
				newRecycler.reader = reader;
				newRecycler.handler = handler;
				newRecycler.closeDate = null;
				Ready ready;
				if (request.isSecure()) {
					ready = secureReadyFactory.create(queue);
				} else {
					ready = readyFactory.create(queue);
				}
				ready.connect(request.getAddress(), new ReadyConnection() {
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
								newRecycler.closeDate = DateUtils.from(DateUtils.from(new Date()) + recyclersTimeToLive);
								Deque<Recycler> oldRecyclers = recyclers.get(request.getAddress());
								if (oldRecyclers == null) {
									oldRecyclers = new LinkedList<Recycler>();
									recyclers.put(request.getAddress(), oldRecyclers);
								}
								oldRecyclers.add(newRecycler);
							}
							@Override
							public void close() {
								write.close();
							}
						};

						newRecycler.write = write;
						write.handle(null, createRequest(request));
						newRecycler.handler.ready(write);
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
		buffer.append(key).append(Http.HEADER_KEY_VALUE_SEPARATOR).append(Http.HEADER_BEFORE_VALUE).append(value).append(Http.CR).append(Http.LF);
	}
	private static ByteBuffer createRequest(HttpRequest request) {
		StringBuilder header = new StringBuilder();
		header.append(request.getMethod().toString()).append(Http.START_LINE_SEPARATOR).append(request.getPath()).append(Http.START_LINE_SEPARATOR).append(Http.HTTP11).append(Http.CR).append(Http.LF);
		
		Map<String, String> headers = request.getHeaders();
		
		for (Map.Entry<String, String> h : headers.entrySet()) {
			appendHeader(header, h.getKey(), h.getValue());
		}
		if (!headers.containsKey(Http.HOST)) {
			appendHeader(header, Http.HOST, request.getAddress().getHost()); // Adding the port looks to fail with Apache/Coyote // + Http.PORT_SEPARATOR + request.getAddress().getPort());
		}
		if (!headers.containsKey(Http.ACCEPT_ENCODING)) {
			appendHeader(header, Http.ACCEPT_ENCODING, Http.GZIP);
		}
		if (!headers.containsKey(Http.CONNECTION)) {
			appendHeader(header, Http.CONNECTION, Http.KEEP_ALIVE);
		}
		
		header.append(Http.CR).append(Http.LF);
		return ByteBuffer.wrap(header.toString().getBytes(Http.USASCII_CHARSET));
	}
	
	private static class RedirectHandler implements HttpClientHandler {
		private final int maxRedirectLevels;
		private final int levelOfRedirect;
		private final HttpClient client;
		private final HttpRequest request;
		private final HttpClientHandler wrappee;
		private boolean redirected = false;
		
		public RedirectHandler(int maxRedirectLevels, int levelOfRedirect, HttpClient client, HttpRequest request, HttpClientHandler wrappee) {
			this.maxRedirectLevels = maxRedirectLevels;
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
				if (levelOfRedirect < maxRedirectLevels) {
					String location = response.getHeaders().get(Http.LOCATION);
					if (location != null) {
						Address newAddress = request.getAddress();
						String newPath;
						String l = null;
						boolean secure = false;
						int defaultPort = Http.DEFAULT_PORT;
						if (location.startsWith(Http.SECURE_PROTOCOL)) {
							l = location.substring(Http.SECURE_PROTOCOL.length());
							secure = true;
							defaultPort = Http.DEFAULT_SECURE_PORT;
						} else if (location.startsWith(Http.PROTOCOL)) {
							l = location.substring(Http.PROTOCOL.length());
						}
						if (l != null) {
							int i = l.indexOf(Http.PATH_SEPARATOR);
							if (i > 0) {
								newPath = l.substring(i);
								l = l.substring(0, i);
							} else {
								newPath = String.valueOf(Http.PATH_SEPARATOR);
							}
							int j = l.indexOf(Http.PORT_SEPARATOR);
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
						
						HttpRequest newRequest = new HttpRequest(newAddress, secure, request.getMethod(), newPath);
						client.send(newRequest, new RedirectHandler(maxRedirectLevels, levelOfRedirect + 1, client, newRequest, wrappee));
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
		public void ready(ByteBufferHandler write) {
			if (redirected) {
				return;
			}
			wrappee.ready(write);
		}
	}

}
