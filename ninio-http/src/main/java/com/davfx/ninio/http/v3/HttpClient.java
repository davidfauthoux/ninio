package com.davfx.ninio.http.v3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferHandler;
import com.davfx.ninio.core.v3.Closing;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.NinioBuilder;
import com.davfx.ninio.core.v3.Queue;
import com.davfx.ninio.core.v3.Receiver;
import com.davfx.ninio.core.v3.SslSocketBuilder;
import com.davfx.ninio.core.v3.TcpSocket;
import com.davfx.ninio.http.HttpHeaderKey;
import com.davfx.ninio.http.HttpHeaderValue;
import com.davfx.ninio.http.HttpResponse;
import com.google.common.base.Charsets;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class HttpClient implements Disconnectable, AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpClient.class);
	
	private static final Config CONFIG = ConfigFactory.load(HttpClient.class.getClassLoader());
	private static final int DEFAULT_MAX_REDIRECTIONS = CONFIG.getInt("ninio.http.redirect.max");

	public static interface Builder extends NinioBuilder<HttpClient> {
		Builder pipelining();
		Builder with(Executor executor);
		Builder with(TcpSocket.Builder connectorFactory);
		Builder withSecure(TcpSocket.Builder secureConnectorFactory);
	}
	
	public static Builder builder() {
		return new Builder() {
			private Executor executor = null;
			private TcpSocket.Builder connectorFactory = TcpSocket.builder();
			private TcpSocket.Builder secureConnectorFactory = new SslSocketBuilder(TcpSocket.builder());
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
				return new HttpClient(queue, executor, connectorFactory, secureConnectorFactory, pipelining);
			}
		};
	}
	
	private final Executor executor;
	private final TcpSocket.Builder connectorFactory;
	private final TcpSocket.Builder secureConnectorFactory;
	
	private static final class ReusableConnector {
		public final Connector connector;
		public final boolean secure;
		public boolean reusable = false;
		public Receiver receiver = null;
		public Closing closing = null;
		
		public ReusableConnector(TcpSocket.Builder factory, Queue queue, boolean secure) {
			this.secure = secure;
			
			factory.receiving(new Receiver() {
				@Override
				public void received(Connector connector, Address address, ByteBuffer buffer) {
					if (receiver != null) {
						receiver.received(connector, address, buffer);
					}
				}
			});
			
			factory.closing(new Closing() {
				@Override
				public void closed() {
					if (closing != null) {
						closing.closed();
					}
				}
			});

			connector = factory.create(queue);
		}
	}
	
	private final Queue queue;
	
	private final Map<Long, ReusableConnector> reusableConnectors = new HashMap<>();
	private long nextReusableConnectorId = 0L;
	
	private final boolean pipelining;
	
	private HttpClient(Queue queue, Executor executor, TcpSocket.Builder connectorFactory, TcpSocket.Builder secureConnectorFactory, boolean pipelining) {
		this.queue = queue;
		this.executor = executor;
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
					connector.connector.close();
				}
				reusableConnectors.clear();
			}
		});
	}

	public HttpReceiverRequestBuilder request() {
		return new HttpReceiverRequestBuilder() {
			private HttpReceiver receiver = null;
			private Failing failing = null;
			private int maxRedirections = DEFAULT_MAX_REDIRECTIONS;

			@Override
			public HttpReceiverRequestBuilder receiving(HttpReceiver receiver) {
				this.receiver = receiver;
				return this;
			}
			@Override
			public HttpReceiverRequestBuilder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			@Override
			public HttpReceiverRequestBuilder maxRedirections(int maxRedirections) {
				this.maxRedirections = maxRedirections;
				return this;
			}
			
			@Override
			public HttpReceiverRequest build() {
				final HttpReceiver r = receiver;
				final Failing f = failing;
				final int thisMaxRedirections = maxRedirections;
				return new HttpReceiverRequest() {
					private long id = -1L;
					private ReusableConnector connector = null;
					private HttpReceiver.ContentReceiver contentReceiver;

					private void prepare(HttpRequest request) {
						final HttpReceiver rr = new RedirectHttpReceiver(HttpClient.this, thisMaxRedirections, request, r, f);
						
						final Disconnectable disconnectable = new Disconnectable() {
							@Override
							public void close() {
								executor.execute(new Runnable() {
									@Override
									public void run() {
										if (connector != null) {
											connector.connector.close();
											reusableConnectors.remove(id);
											connector = null;
										}
									}
								});
							}
						};
						
						final InnerReceiver innerReceiver = new InnerReceiver() {
							@Override
							public void received(ByteBuffer buffer) {
								contentReceiver.received(buffer);
							}
							@Override
							public void received(HttpResponse response) {
								contentReceiver = rr.received(disconnectable, response);
							}
							@Override
							public void ended() {
								contentReceiver.ended();
								contentReceiver = null;
								if (connector != null) {
									if (!pipelining) {
										if (responseKeepAlive) {
											connector.reusable = true;
										} else {
											connector.connector.close();
											reusableConnectors.remove(id);
										}
										connector = null;
									}
								}
							}
						};
						
						connector.closing = new Closing() {
							@Override
							public void closed() {
								executor.execute(new Runnable() {
									@Override
									public void run() {
										reusableConnectors.remove(id);
				
										if (connector == null) {
											return;
										}
										
										try {
											parseClosed(innerReceiver);
										} catch (IOException ioe) {
											f.failed(ioe);
										}
				
										connector = null;
										contentReceiver = null;
									}
								});
							}
						};
		
						connector.receiver = new Receiver() {
							@Override
							public void received(Connector c, final Address address, final ByteBuffer buffer) {
								executor.execute(new Runnable() {
									@Override
									public void run() {
										if (connector == null) {
											return;
										}
										
										try {
											parse(buffer, innerReceiver);
										} catch (IOException ioe) {
											LOGGER.error("Connection error", ioe);
											connector.connector.close();
											reusableConnectors.remove(id);
											connector = null;
											contentReceiver = null;
											f.failed(ioe);
										}
									}
								});
							}
						};
						
						LOGGER.trace("Sending request: {}", request);
						connector.connector.send(null, request.toByteBuffer());
					}
					
					@Override
					public Send create(final HttpRequest request) {
						// HTTP 1.1 assumed
						
						long headerContentLength = -1L;
						for (String contentLengthValue : request.headers.get(HttpHeaderKey.CONTENT_LENGTH)) {
							try {
								headerContentLength = Long.parseLong(contentLengthValue);
							} catch (NumberFormatException e) {
								LOGGER.error("Invalid Content-Length: {}", contentLengthValue);
							}
						}
						final long requestContentLength = headerContentLength;
						
						GzipWriter headerGzip = null;
						for (String contentEncodingValue : request.headers.get(HttpHeaderKey.CONTENT_ENCODING)) {
							if (contentEncodingValue.equalsIgnoreCase(HttpHeaderValue.GZIP)) {
								headerGzip = new GzipWriter();
							}
						}
						final GzipWriter requestGzip = headerGzip;
						
						boolean headerChunked = (requestContentLength < 0L);
						for (String transferEncodingValue : request.headers.get(HttpHeaderKey.TRANSFER_ENCODING)) {
							headerChunked = transferEncodingValue.equalsIgnoreCase(HttpHeaderValue.CHUNKED);
						}
						final boolean requestChunked = headerChunked;
		
						/* Ignored
						boolean headerKeepAlive = true;
						for (String connectionValue : headers.get(HttpHeaderKey.CONNECTION)) {
							if (connectionValue.equalsIgnoreCase(HttpHeaderValue.CLOSE)) {
								headerKeepAlive = false;
							} else if (connectionValue.equalsIgnoreCase(HttpHeaderValue.KEEP_ALIVE)) {
								headerKeepAlive = true;
							}
						}
						final boolean requestKeepAlive = headerKeepAlive;
						*/
						
						executor.execute(new Runnable() {
							@Override
							public void run() {
								if (id >= 0L) {
									throw new IllegalStateException("Could not be created twice");
								}
								
								for (Map.Entry<Long, ReusableConnector> e : reusableConnectors.entrySet()) {
									long reusedId = e.getKey();
									ReusableConnector reusedConnector = e.getValue();
									if (reusedConnector.reusable && (reusedConnector.secure == request.secure)) {
										id = reusedId;
		
										LOGGER.trace("Recycling connection {}", id);
										
										connector = reusedConnector;
										reusedConnector.reusable = false;
										prepare(request);
										return;
									}
								}
		
								id = nextReusableConnectorId;
								nextReusableConnectorId++;
								
								connector = new ReusableConnector(request.secure ? secureConnectorFactory.to(request.address) : connectorFactory.to(request.address), queue, request.secure);
								reusableConnectors.put(id, connector);
		
								prepare(request);
							}
						});
		
						return new Send() {
							private final ByteBuffer emptyLineByteBuffer = LineReader.toBuffer("");
							private final ByteBuffer zeroByteBuffer = LineReader.toBuffer(Integer.toHexString(0));

							@Override
							public Send post(final ByteBuffer buffer) {
								if (!buffer.hasRemaining()) {
									return this;
								}

								executor.execute(new Runnable() {
									@Override
									public void run() {
										if (connector != null) {
											if (requestGzip != null) {
												requestGzip.handle(buffer, new ByteBufferHandler() {
													@Override
													public void handle(Address address, ByteBuffer buffer) {
														if (buffer.hasRemaining()) {
															if (requestChunked) {
																connector.connector.send(null, LineReader.toBuffer(Integer.toHexString(buffer.remaining())));
															}
															connector.connector.send(null, buffer);
															if (requestChunked) {
																connector.connector.send(null, emptyLineByteBuffer);
															}
														}
													}
												});
											} else {
												if (requestChunked) {
													connector.connector.send(null, LineReader.toBuffer(Integer.toHexString(buffer.remaining())));
												}
												connector.connector.send(null, buffer);
												if (requestChunked) {
													connector.connector.send(null, emptyLineByteBuffer);
												}
											}
										}
									}
								});
								return this;
							}
							
							@Override
							public void finish() {
								executor.execute(new Runnable() {
									@Override
									public void run() {
										if (connector != null) {
											if (requestGzip != null) {
												requestGzip.close(new ByteBufferHandler() {
													@Override
													public void handle(Address address, ByteBuffer buffer) {
														if (buffer.hasRemaining()) {
															if (requestChunked) {
																connector.connector.send(null, LineReader.toBuffer(Integer.toHexString(buffer.remaining())));
															}
															connector.connector.send(null, buffer);
															if (requestChunked) {
																connector.connector.send(null, emptyLineByteBuffer);
															}
														}

														if (requestChunked) {
															connector.connector.send(null, zeroByteBuffer);
															connector.connector.send(null, emptyLineByteBuffer);
														}
													}
												});
											} else {
												if (requestChunked) {
													connector.connector.send(null, zeroByteBuffer);
													connector.connector.send(null, emptyLineByteBuffer);
												}
											}
											
											if (pipelining) {
												connector.reusable = true;
												connector = null;
											}
										}
									}
								});
							}
		
							@Override
							public void cancel() {
								executor.execute(new Runnable() {
									@Override
									public void run() {
										if (connector != null) {
											connector.connector.close();
											reusableConnectors.remove(id);
											connector = null;
										}
									}
								});
							}
						};
					}
					
					//
					
					private final LineReader lineReader = new LineReader();
					private boolean responseLineRead = false;
					private boolean headersRead = false;
		
					private boolean responseKeepAlive = false;
		
					private int responseCode;
					private String responseReason;
					private boolean http11;
					private final Multimap<String, String> headers = HashMultimap.create();
					private boolean responseChunked;
					private long responseContentLength;
					private GzipReader responseGzip;
		
					private boolean chunkHeaderRead = false;
					private int chunkLength;
					private int chunkCountRead;
					private int countRead;
					
					private void addHeader(String headerLine) throws IOException {
						int i = headerLine.indexOf(HttpSpecification.HEADER_KEY_VALUE_SEPARATOR);
						if (i < 0) {
							throw new IOException("Invalid header: " + headerLine);
						}
						String key = headerLine.substring(0, i);
						String value = headerLine.substring(i + 1).trim();
						headers.put(key, value);
					}
					
					private void parseResponseLine(String responseLine) throws IOException {
						int i = responseLine.indexOf(HttpSpecification.START_LINE_SEPARATOR);
						if (i < 0) {
							throw new IOException("Invalid response: " + responseLine);
						}
						int j = responseLine.indexOf(HttpSpecification.START_LINE_SEPARATOR, i + 1);
						if (j < 0) {
							throw new IOException("Invalid response: " + responseLine);
						}
						String responseVersion = responseLine.substring(0, i);
						if (responseVersion.equals(HttpSpecification.HTTP10)) {
							http11 = false;
						} else if (responseVersion.equals(HttpSpecification.HTTP11)) {
							http11 = true;
						} else {
							throw new IOException("Unsupported version");
						}
						String code = responseLine.substring(i + 1, j);
						try {
							responseCode = Integer.parseInt(code);
						} catch (NumberFormatException e) {
							throw new IOException("Invalid status code: " + code);
						}
						responseReason = responseLine.substring(j + 1);
					}
				
					private void parse(ByteBuffer buffer, final InnerReceiver receiver) throws IOException {
						while (!responseLineRead) {
							String line = lineReader.handle(buffer);
							if (line == null) {
								return;
							}
							LOGGER.trace("Response line: {}", line);
							parseResponseLine(line);
							responseLineRead = true;
						}
						
						while (!headersRead) {
							String line = lineReader.handle(buffer);
							if (line == null) {
								return;
							}
							if (line.isEmpty()) {
								LOGGER.trace("Header line empty");
								headersRead = true;
								
								responseContentLength = -1L;
								for (String contentLengthValue : headers.get(HttpHeaderKey.CONTENT_LENGTH)) {
									try {
										responseContentLength = Long.parseLong(contentLengthValue);
									} catch (NumberFormatException e) {
										LOGGER.error("Invalid Content-Length: {}", contentLengthValue);
									}
								}
								
								responseGzip = null;
								for (String contentEncodingValue : headers.get(HttpHeaderKey.CONTENT_ENCODING)) {
									if (contentEncodingValue.equalsIgnoreCase(HttpHeaderValue.GZIP)) {
										responseGzip = new GzipReader();
									}
								}
								
								responseChunked = (responseContentLength < 0L);
								for (String transferEncodingValue : headers.get(HttpHeaderKey.TRANSFER_ENCODING)) {
									responseChunked = transferEncodingValue.equalsIgnoreCase(HttpHeaderValue.CHUNKED);
								}
				
								responseKeepAlive = http11;
								for (String connectionValue : headers.get(HttpHeaderKey.CONNECTION)) {
									if (connectionValue.equalsIgnoreCase(HttpHeaderValue.CLOSE)) {
										responseKeepAlive = false;
									} else if (connectionValue.equalsIgnoreCase(HttpHeaderValue.KEEP_ALIVE)) {
										responseKeepAlive = true;
									}
								}
								LOGGER.trace("Keep alive = {}", responseKeepAlive);
								
								receiver.received(new HttpResponse(responseCode, responseReason, ImmutableMultimap.copyOf(headers)));
								
								countRead = 0;
							} else {
								LOGGER.trace("Header line: {}", line);
								addHeader(line);
							}
						}
					
						if (responseChunked) {
							
							while (true) {
							
								while (!chunkHeaderRead) {
									String line = lineReader.handle(buffer);
									if (line == null) {
										return;
									}
									try {
										chunkLength = Integer.parseInt(line, 16);
									} catch (NumberFormatException e) {
										throw new IOException("Invalid chunk size: " + line);
									}
									chunkHeaderRead = true;
									chunkCountRead = 0;
								}

								if (chunkHeaderRead) {
									if (chunkCountRead < chunkLength) {
										if (!buffer.hasRemaining()) {
											return;
										}
										long toRead = chunkLength - chunkCountRead;
										handleContent(buffer, toRead, receiver);
									}
										
									if (chunkCountRead == chunkLength) {
										while (chunkHeaderRead) {
											String line = lineReader.handle(buffer);
											if (line == null) {
												return;
											}
											if (!line.isEmpty()) {
												throw new IOException("Invalid chunk footer");
											}
											chunkHeaderRead = false;
										}

										if (chunkLength == 0) {
											if (buffer.hasRemaining()) { //TODO pipeling
												throw new IOException("Connection reset, too much data in chunked stream: " + new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining(), Charsets.UTF_8));
											}

											receiver.ended();
										}
									}
								}
							
							}
				
						} else {
						
							if (responseContentLength >= 0) {
								if (countRead < responseContentLength) {
									long toRead = responseContentLength - countRead;
									handleContent(buffer, toRead, receiver);
								}
								if (countRead == responseContentLength) {
									if (buffer.hasRemaining()) { //TODO pipeling
										throw new IOException("Connection reset, too much data");
									}
									receiver.ended();
								}
							} else {
								handleContent(buffer, -1, receiver);
							}
				
						}
					}
					
				    private void handleContent(ByteBuffer buffer, long toRead, final InnerReceiver receiver) throws IOException {
				    	ByteBuffer deflated = buffer.duplicate(); // We must duplicated this buffer because it can be used later by the user, but must be continued in the handle loop
				    	if (toRead >= 0) {
							if (deflated.remaining() > toRead) {
								deflated.limit((int) (buffer.position() + toRead));
							}
				    	}
				    	
				    	chunkCountRead += deflated.remaining();
						countRead += deflated.remaining();
						buffer.position((int) (buffer.position() + deflated.remaining()));
				
						LOGGER.trace("Content received (read = {})", countRead);
						
						if (responseGzip == null) {
							receiver.received(deflated);
							return;
						}
						
						responseGzip.handle(deflated, new ByteBufferHandler() {
							@Override
							public void handle(Address address, ByteBuffer buffer) {
								receiver.received(buffer);
							}
						});
				    }
				    
					private void parseClosed(InnerReceiver receiver) throws IOException {
						if (responseChunked) {
							if (chunkHeaderRead) {
								throw new IOException("Connection reset by peer in chunked stream");
							}
							receiver.ended();
						} else {
							if (responseContentLength < 0L) {
								receiver.ended();
								return;
							}
			
							if (countRead < responseContentLength) {
								throw new IOException("Connection reset by peer, expecting more data (read = " + countRead + ", contentLength = " + responseContentLength + ")");
							}
						}
					}
				};
			}
		};
	}
	
	private static interface InnerReceiver {
		void received(HttpResponse response);
		void received(ByteBuffer buffer);
		void ended();
	}
}
