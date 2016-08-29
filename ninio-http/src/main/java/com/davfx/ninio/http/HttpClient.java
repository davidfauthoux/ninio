package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.SecureSocketBuilder;
import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.core.TcpSocket;
import com.davfx.ninio.dns.DnsConnecter;
import com.davfx.ninio.util.ConfigUtils;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.typesafe.config.Config;

public final class HttpClient implements HttpConnecter {
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpClient.class);
	
	private static final Config CONFIG = ConfigUtils.load(new com.davfx.ninio.http.dependencies.Dependencies(), HttpClient.class);
	private static final int DEFAULT_MAX_REDIRECTIONS = CONFIG.getInt("redirect.max");

	private static final String DEFAULT_USER_AGENT = "ninio"; // Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/38.0.2125.111 Safari/537.36";
	private static final String DEFAULT_ACCEPT = "*/*";

	public static interface Builder extends NinioBuilder<HttpConnecter> {
		Builder pipelining();
		Builder with(Executor executor);
		Builder with(DnsConnecter dns);
		Builder with(TcpSocket.Builder connectorFactory);
		Builder withSecure(TcpSocket.Builder secureConnectorFactory);
	}
	
	public static Builder builder() {
		return new Builder() {
			private Executor executor = null;
			private DnsConnecter dns = null;
			private TcpSocket.Builder connectorFactory = TcpSocket.builder();
			private TcpSocket.Builder secureConnectorFactory = new SecureSocketBuilder(TcpSocket.builder());
			private boolean pipelining = false;
			
			@Override
			public Builder pipelining() {
				pipelining = true;
				return this;
			}
			
			@Override
			public Builder with(Executor executor) {
				this.executor = executor;
				return this;
			}
			
			@Override
			public Builder with(DnsConnecter dns) {
				this.dns = dns;
				return this;
			}
			
			@Override
			public Builder with(TcpSocket.Builder connectorFactory) {
				this.connectorFactory = connectorFactory;
				return this;
			}

			@Override
			public Builder withSecure(TcpSocket.Builder secureConnectorFactory) {
				this.secureConnectorFactory = secureConnectorFactory;
				return this;
			}

			@Override
			public HttpClient create(Queue queue) {
				if (executor == null) {
					throw new NullPointerException("executor");
				}
				if (dns == null) {
					throw new NullPointerException("dns");
				}
				return new HttpClient(queue, executor, dns, connectorFactory, secureConnectorFactory, pipelining);
			}
		};
	}
	
	private final Executor executor;
	private final DnsConnecter dns;
	private final TcpSocket.Builder connectorFactory;
	private final TcpSocket.Builder secureConnectorFactory;
	
	private static final class ReusableConnector {
		public Connecter connecting;
		public final Address address;
		public final boolean secure;

		public boolean reusable = true;

		private Connection receiver = null;
		private final Deque<Connection> nextReceivers = new LinkedList<>();
		
		public ReusableConnector(Address address, boolean secure) {
			this.address = address;
			this.secure = secure;
		}
		
		public void launch(final Executor executor, TcpSocket.Builder factory, Queue queue, final Runnable onClose) {
			connecting = factory.create(queue);
			
			connecting.connect(new Connection() {
				@Override
				public void received(Address address, final ByteBuffer buffer) {
					executor.execute(new Runnable() {
						@Override
						public void run() {
							while (buffer.hasRemaining()) {
								if (receiver == null) {
									break;
								}
								receiver.received(null, buffer);
							}
						}
					});
				}
				
				@Override
				public void connected(Address address) {
				}
				
				@Override
				public void closed() {
					executor.execute(new Runnable() {
						@Override
						public void run() {
							if (receiver != null) {
								receiver.closed();
							}

							onClose.run();
						}
					});
				}

				@Override
				public void failed(final IOException ioe) {
					executor.execute(new Runnable() {
						@Override
						public void run() {
							if (receiver != null) {
								receiver.failed(ioe);
							}

							onClose.run();
						}
					});
				}
			});
		}
		
		public void failAllNext(IOException ioe) {
			IOException e = new IOException("Error in pipeline", ioe);
			for (Connection r : nextReceivers) {
				r.failed(e);
			}
			nextReceivers.clear();
			receiver = null;
			connecting.close();
		}
		
		public void push(Connection receiver) {
			if (this.receiver == null) {
				this.receiver = receiver;
			} else {
				nextReceivers.addLast(receiver);
			}
		}
		
		public void pop() {
			if (!nextReceivers.isEmpty()) {
				LOGGER.trace("To next receiver");
				receiver = nextReceivers.removeFirst();
			} else {
				LOGGER.trace("Nothing to pop");
				receiver = null;
			}
		}
	}
	
	private final Queue queue;
	
	private final Map<Long, ReusableConnector> reusableConnectors = new HashMap<>();
	private long nextReusableConnectorId = 0L;
	
	private final boolean pipelining;
	
	private final ByteBuffer emptyLineByteBuffer = LineReader.toBuffer("");
	
	private final Map<String, String> headerSanitization = new HashMap<String, String>();
	
	{
		headerSanitization.put(HttpHeaderKey.CONTENT_LENGTH.toLowerCase(), HttpHeaderKey.CONTENT_LENGTH);
		headerSanitization.put(HttpHeaderKey.CONTENT_ENCODING.toLowerCase(), HttpHeaderKey.CONTENT_ENCODING);
		headerSanitization.put(HttpHeaderKey.TRANSFER_ENCODING.toLowerCase(), HttpHeaderKey.TRANSFER_ENCODING);
		headerSanitization.put(HttpHeaderKey.CONNECTION.toLowerCase(), HttpHeaderKey.CONNECTION);
		headerSanitization.put(HttpHeaderKey.ACCEPT_ENCODING.toLowerCase(), HttpHeaderKey.ACCEPT_ENCODING);

		headerSanitization.put(HttpHeaderKey.HOST.toLowerCase(), HttpHeaderKey.HOST);
		headerSanitization.put(HttpHeaderKey.USER_AGENT.toLowerCase(), HttpHeaderKey.USER_AGENT);
		headerSanitization.put(HttpHeaderKey.ACCEPT.toLowerCase(), HttpHeaderKey.ACCEPT);
	}

	private HttpClient(Queue queue, Executor executor, DnsConnecter dns, TcpSocket.Builder connectorFactory, TcpSocket.Builder secureConnectorFactory, boolean pipelining) {
		this.queue = queue;
		this.executor = executor;
		this.dns = dns;
		this.connectorFactory = connectorFactory;
		this.secureConnectorFactory = secureConnectorFactory;
		this.pipelining = pipelining;
	}

	@Override
	public void close() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				for (ReusableConnector connector : reusableConnectors.values()) {
					LOGGER.trace("Closing underlying connection");
					connector.connecting.close();
				}
				reusableConnectors.clear();
			}
		});
	}

	@Override
	public HttpRequestBuilder request() {
		return new HttpRequestBuilder() {
			private int maxRedirections = DEFAULT_MAX_REDIRECTIONS;

			@Override
			public HttpRequestBuilder maxRedirections(int maxRedirections) {
				this.maxRedirections = maxRedirections;
				return this;
			}
			
			private HttpReceiver callback = null;
			private HttpContentSender contentSender = null;
			
			@Override
			public HttpRequestBuilderHttpContentSender build(final HttpRequest request) {
				if (contentSender != null) {
					throw new IllegalStateException();
				}
				
				final int thisMaxRedirections = maxRedirections;
				
				contentSender = new HttpContentSender() {
					private boolean emptyBody = true;
					private HttpContentSender sender = null;

					private long id = -1L;
					private HttpVersion requestVersion;
					private Multimap<String, String> completedHeaders;
					
					private ReusableConnector reusableConnector = null;
					
					private boolean closed = false;

					private void abruptlyClose(IOException ioe) {
						LOGGER.trace("Abruptly closed ({})", ioe.getMessage());
						
						reusableConnector.failAllNext(ioe);
						reusableConnectors.remove(id);
					}
					
					private void abruptlyCloseAndFail(IOException e) {
						LOGGER.trace("Error", e);
						abruptlyClose(e);
						
						if (!closed) {
							closed = true;
							callback.failed(e);
						}
					}

					private void sendRequest() {
						sender = new HttpContentSender() {
							@Override
							public HttpContentSender send(ByteBuffer buffer, SendCallback callback) {
								reusableConnector.connecting.send(null, buffer, callback);
								return this;
							}

							@Override
							public void finish() {
								reusableConnector.reusable = true;
							}
		
							@Override
							public void cancel() {
								abruptlyCloseAndFail(new IOException("Canceled"));
							}
						};
						
						requestVersion = HttpVersion.HTTP11;
						
						completedHeaders = ArrayListMultimap.create(request.headers);
						if (emptyBody && ((request.method  == HttpMethod.POST) || (request.method  == HttpMethod.PUT))) {
							completedHeaders.put(HttpHeaderKey.CONTENT_LENGTH, String.valueOf(0L));
						}
						/*%%
						if (!completedHeaders.containsKey(HttpHeaderKey.HOST)) {
							String portSuffix;
							if ((request.secure && (request.address.port != HttpSpecification.DEFAULT_SECURE_PORT))
							|| (!request.secure && (request.address.port != HttpSpecification.DEFAULT_PORT))) {
								portSuffix = String.valueOf(HttpSpecification.PORT_SEPARATOR) + String.valueOf(request.address.port);
							} else {
								portSuffix = "";
							}
							completedHeaders.put(HttpHeaderKey.HOST, request.host + portSuffix);
						}
						*/
						
						boolean headerKeepAlive = (requestVersion == HttpVersion.HTTP11);
						boolean automaticallySetGzipChunked = headerKeepAlive && (request.method == HttpMethod.POST);
						for (String connectionValue : completedHeaders.get(HttpHeaderKey.CONNECTION)) {
							if (connectionValue.equalsIgnoreCase(HttpHeaderValue.CLOSE)) {
								headerKeepAlive = false;
							} else if (connectionValue.equalsIgnoreCase(HttpHeaderValue.KEEP_ALIVE)) {
								headerKeepAlive = true;
							} else {
								automaticallySetGzipChunked = false;
							}
						}
						final boolean requestKeepAlive = headerKeepAlive;

						if (!completedHeaders.containsKey(HttpHeaderKey.ACCEPT_ENCODING)) {
							completedHeaders.put(HttpHeaderKey.ACCEPT_ENCODING, HttpHeaderValue.GZIP);
						}
						
						if (automaticallySetGzipChunked && !completedHeaders.containsKey(HttpHeaderKey.CONTENT_ENCODING) && !completedHeaders.containsKey(HttpHeaderKey.CONTENT_LENGTH)) { // Content-Length MUST refer to the compressed data length, which the user is not aware of, thus we CANNOT compress if the user specifies a Content-Length
							completedHeaders.put(HttpHeaderKey.CONTENT_ENCODING, HttpHeaderValue.GZIP);
						}
						if (automaticallySetGzipChunked && !completedHeaders.containsKey(HttpHeaderKey.CONTENT_LENGTH) && !completedHeaders.containsKey(HttpHeaderKey.TRANSFER_ENCODING)) {
							completedHeaders.put(HttpHeaderKey.TRANSFER_ENCODING, HttpHeaderValue.CHUNKED);
						}

						if (!completedHeaders.containsKey(HttpHeaderKey.CONNECTION)) {
							completedHeaders.put(HttpHeaderKey.CONNECTION, HttpHeaderValue.KEEP_ALIVE);
						}
						if (!completedHeaders.containsKey(HttpHeaderKey.USER_AGENT)) {
							completedHeaders.put(HttpHeaderKey.USER_AGENT, DEFAULT_USER_AGENT);
						}
						if (!completedHeaders.containsKey(HttpHeaderKey.ACCEPT)) {
							completedHeaders.put(HttpHeaderKey.ACCEPT, DEFAULT_ACCEPT);
						}

						for (String transferEncodingValue : completedHeaders.get(HttpHeaderKey.TRANSFER_ENCODING)) {
							if (transferEncodingValue.equalsIgnoreCase(HttpHeaderValue.CHUNKED)) {
								LOGGER.trace("Request is chunked");
								sender = new ChunkedWriter(sender);
							}
							break;
						}
		
						for (String contentLengthValue : completedHeaders.get(HttpHeaderKey.CONTENT_LENGTH)) {
							try {
								long headerContentLength = Long.parseLong(contentLengthValue);
								LOGGER.trace("Request content length: {}", headerContentLength);
								sender = new ContentLengthWriter(headerContentLength, sender);
							} catch (NumberFormatException e) {
								LOGGER.error("Invalid Content-Length: {}", contentLengthValue);
							}
							break;
						}
						
						for (String contentEncodingValue : completedHeaders.get(HttpHeaderKey.CONTENT_ENCODING)) {
							if (contentEncodingValue.equalsIgnoreCase(HttpHeaderValue.GZIP)) {
								LOGGER.trace("Request is gzip");
								sender = new GzipWriter(sender);
							}
							break;
						}
						
						if (id >= 0L) {
							throw new IllegalStateException("Could not be created twice");
						}
						
						if (requestKeepAlive) {
							LOGGER.trace("Connections = {}", reusableConnectors);
							for (Map.Entry<Long, ReusableConnector> e : reusableConnectors.entrySet()) {
								long reusedId = e.getKey();
								ReusableConnector reusedConnector = e.getValue();
								if (((pipelining && reusedConnector.reusable) || (!pipelining && (reusedConnector.receiver == null))) && reusedConnector.address.equals(request.address) && (reusedConnector.secure == request.secure)) {
									id = reusedId;
	
									LOGGER.trace("Recycling connection (id = {})", id);
									
									reusableConnector = reusedConnector;
									reusableConnector.reusable = false;
									break;
								} else if (!reusedConnector.reusable) {
									LOGGER.trace("Connection not reusable (id = {})", reusedId);
								}
							}
						}

						if (reusableConnector == null) {
							id = nextReusableConnectorId;
							nextReusableConnectorId++;
	
							LOGGER.trace("Creating a new connection (id = {})", id);
	
							reusableConnector = new ReusableConnector(request.address, request.secure);
							reusableConnectors.put(id, reusableConnector);
							reusableConnector.launch(executor, request.secure ? secureConnectorFactory.to(request.address) : connectorFactory.to(request.address), queue, new Runnable() {
								@Override
								public void run() {
									LOGGER.trace("Connection removed (id = {})", id);
									abruptlyClose(new IOException("Connection closed"));
								}
							});
	
							reusableConnector.reusable = false;
						}
	
						//
						
						final HttpReceiver redirectingReceiver = new RedirectHttpReceiver(dns, HttpClient.this, thisMaxRedirections, request, new HttpReceiver() {
							@Override
							public HttpContentReceiver received(HttpResponse response) {
								return callback.received(response);
							}
							
							@Override
							public void failed(IOException ioe) {
								abruptlyCloseAndFail(ioe);
							}
						});
						
						reusableConnector.push(new Connection() {
							private final LineReader lineReader = new LineReader();
							private boolean responseLineRead = false;
							private boolean responseHeadersRead;
				
							private boolean responseKeepAlive = false;
				
							private int responseCode;
							private String responseReason;
							private HttpVersion responseVersion;
							private final Multimap<String, String> responseHeaders = HashMultimap.create();
							
							private HttpContentReceiver responseReceiver = new HttpContentReceiver() {
								@Override
								public void received(ByteBuffer buffer) {
									abruptlyCloseAndFail(new IOException("Connection closed because should not received data"));
								}
								@Override
								public void ended() {
									abruptlyCloseAndFail(new IOException("Connection closed without any response received"));
								}
							};
							
							private boolean addHeader(String headerLine) {
								int i = headerLine.indexOf(HttpSpecification.HEADER_KEY_VALUE_SEPARATOR);
								if (i < 0) {
									abruptlyCloseAndFail(new IOException("Invalid header: " + headerLine));
									return false;
								}
								String key = headerLine.substring(0, i);
								String sanitizedKey = headerSanitization.get(key.toLowerCase());
								if (sanitizedKey != null) {
									key = sanitizedKey;
								}
								String value = headerLine.substring(i + 1).trim();
								responseHeaders.put(key, value);
								return true;
							}
							
							private boolean parseResponseLine(String responseLine) {
								int i = responseLine.indexOf(HttpSpecification.START_LINE_SEPARATOR);
								if (i < 0) {
									abruptlyCloseAndFail(new IOException("Invalid response: " + responseLine));
									return false;
								}
								int j = responseLine.indexOf(HttpSpecification.START_LINE_SEPARATOR, i + 1);
								if (j < 0) {
									abruptlyCloseAndFail(new IOException("Invalid response: " + responseLine));
									return false;
								}
								String version = responseLine.substring(0, i);
								if (!version.startsWith(HttpSpecification.HTTP_VERSION_PREFIX)) {
									abruptlyCloseAndFail(new IOException("Unsupported version: " + version));
									return false;
								}
								version = version.substring(HttpSpecification.HTTP_VERSION_PREFIX.length());
								if (version.equals(HttpVersion.HTTP10.toString())) {
									responseVersion = HttpVersion.HTTP10;
								} else if (version.equals(HttpVersion.HTTP11.toString())) {
									responseVersion = HttpVersion.HTTP11;
								} else {
									abruptlyCloseAndFail(new IOException("Unsupported version: " + version));
									return false;
								}
								String code = responseLine.substring(i + 1, j);
								try {
									responseCode = Integer.parseInt(code);
								} catch (NumberFormatException e) {
									abruptlyCloseAndFail(new IOException("Invalid status code: " + code));
									return false;
								}
								responseReason = responseLine.substring(j + 1);
								return true;
							}
							
							@Override
							public void connected(Address address) {
							}
							
							@Override
							public void closed() {
								responseReceiver.ended();
								
								abruptlyClose(new IOException("Connection closed by peer"));
							}
							
							@Override
							public void failed(IOException e) {
								abruptlyCloseAndFail(e);
							}
							
							@Override
							public void received(Address address, ByteBuffer buffer) {
								while (!responseLineRead) {
									String line = lineReader.handle(buffer);
									if (line == null) {
										return;
									}
									LOGGER.trace("Response line: {}", line);
									if (!parseResponseLine(line)) {
										return;
									}
									responseLineRead = true;
									responseHeadersRead = false;
								}
								
								while (!responseHeadersRead) {
									String line = lineReader.handle(buffer);
									if (line == null) {
										return;
									}
									if (line.isEmpty()) {
										LOGGER.trace("Header line empty");
										responseHeadersRead = true;
										
										final HttpContentReceiver receiver = redirectingReceiver.received(new HttpResponse(responseCode, responseReason, ImmutableMultimap.copyOf(responseHeaders)));
										
										responseReceiver = new HttpContentReceiver() {
											@Override
											public void received(ByteBuffer buffer) {
												if (receiver != null) {
													receiver.received(buffer.duplicate());
												}
												buffer.position(buffer.position() + buffer.remaining());
											}
											@Override
											public void ended() {
												if (receiver != null) {
													LOGGER.trace("Correctly ended");
													receiver.ended();
												}
												
												if (!responseKeepAlive) {
													abruptlyClose(new IOException("Connection closed because not kept alive"));
												}

												reusableConnector.pop();
											}
										};

										responseKeepAlive = (responseVersion != HttpVersion.HTTP10);
										for (String connectionValue : responseHeaders.get(HttpHeaderKey.CONNECTION)) {
											if (connectionValue.equalsIgnoreCase(HttpHeaderValue.CLOSE)) {
												responseKeepAlive = false;
											} else if (connectionValue.equalsIgnoreCase(HttpHeaderValue.KEEP_ALIVE)) {
												responseKeepAlive = true;
											}
										}
										LOGGER.trace("Keep alive = {}", responseKeepAlive);

										Failing failing = new Failing() {
											@Override
											public void failed(IOException ioe) {
												abruptlyCloseAndFail(ioe);
											}
										};
										
										for (String contentEncodingValue : responseHeaders.get(HttpHeaderKey.CONTENT_ENCODING)) {
											if (contentEncodingValue.equalsIgnoreCase(HttpHeaderValue.GZIP)) {
												responseReceiver = new GzipReader(failing, responseReceiver);
											}
											break;
										}
										
										for (String contentLengthValue : responseHeaders.get(HttpHeaderKey.CONTENT_LENGTH)) {
											try {
												long responseContentLength = Long.parseLong(contentLengthValue);
												responseReceiver = new ContentLengthReader(responseContentLength, failing, responseReceiver);
											} catch (NumberFormatException e) {
												LOGGER.error("Invalid Content-Length: {}", contentLengthValue);
											}
											break;
										}
										
										for (String transferEncodingValue : responseHeaders.get(HttpHeaderKey.TRANSFER_ENCODING)) {
											if (transferEncodingValue.equalsIgnoreCase(HttpHeaderValue.CHUNKED)) {
												responseReceiver = new ChunkedReader(failing, responseReceiver);
											}
											break;
										}
						
									} else {
										LOGGER.trace("Header line: {}", line);
										if (!addHeader(line)) {
											return;
										}
									}
								}
								
								responseReceiver.received(buffer);
							}
						});
						
						LOGGER.trace("Sending request: {} (complete headers = {})", request, completedHeaders);
						
						SendCallback sendCallback = new SendCallback() {
							@Override
							public void sent() {
							}
							@Override
							public void failed(IOException ioe) {
								abruptlyCloseAndFail(ioe);
							}
						};
						
						reusableConnector.connecting.send(null, LineReader.toBuffer(request.method.toString() + HttpSpecification.START_LINE_SEPARATOR + request.path + HttpSpecification.START_LINE_SEPARATOR + HttpSpecification.HTTP_VERSION_PREFIX + requestVersion.toString()), sendCallback);

						for (Map.Entry<String, String> h : completedHeaders.entries()) {
							String k = h.getKey();
							String v = h.getValue();
							reusableConnector.connecting.send(null, LineReader.toBuffer(k + HttpSpecification.HEADER_KEY_VALUE_SEPARATOR + HttpSpecification.HEADER_BEFORE_VALUE + v), sendCallback);
						}
						
						reusableConnector.connecting.send(null, emptyLineByteBuffer.duplicate(), sendCallback);
					}
					
					@Override
					public HttpContentSender send(final ByteBuffer buffer, final SendCallback sendCallback) {
						if (callback == null) {
							throw new IllegalStateException();
						}
						
						executor.execute(new Runnable() {
							@Override
							public void run() {
								if (closed) {
									callback.failed(new IOException("Closed"));
									return;
								}
								
								emptyBody = false;
								
								if (sender == null) {
									sendRequest();
								}
								
								sender.send(buffer, sendCallback);
							}
						});
						return this;
					}

					@Override
					public void finish() {
						if (callback == null) {
							throw new IllegalStateException();
						}
						
						executor.execute(new Runnable() {
							@Override
							public void run() {
								if (closed) {
									return;
								}
								
								if (sender == null) {
									sendRequest();
								}
								
								sender.finish();
							}
						});
					}

					@Override
					public void cancel() {
						if (callback == null) {
							throw new IllegalStateException();
						}
						
						executor.execute(new Runnable() {
							@Override
							public void run() {
								if (closed) {
									return;
								}
								
								if (sender == null) {
									return;
								}
								
								sender.cancel();
							}
						});
					}
				};

				return new HttpRequestBuilderHttpContentSenderImpl(this, contentSender);
			}
			
			@Override
			public HttpContentSender receive(HttpReceiver c) {
				if (contentSender == null) {
					throw new IllegalStateException();
				}
				if (callback != null) {
					throw new IllegalStateException();
				}
				
				callback = c;
				return contentSender;
			}
		};
	}
}
