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
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.v3.Closing;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.ConnectorFactory;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.Receiver;
import com.davfx.ninio.core.v3.Shared;
import com.davfx.ninio.core.v3.SocketConnectorFactory;
import com.davfx.ninio.http.HttpHeaderKey;
import com.davfx.ninio.http.HttpHeaderValue;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpSpecification;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

public final class HttpClient implements AutoCloseable, Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpClient.class);

	private Executor executor = Shared.EXECUTOR;
	private ConnectorFactory connectorFactory = new SocketConnectorFactory();
	private ConnectorFactory secureConnectorFactory = new SocketConnectorFactory();
	
	private static final class ReusableConnector {
		public final Connector connector;
		public final boolean secure;
		public boolean reusable = false;
		public Receiver receiver = null;
		public Closing closing = null;
		public ReusableConnector(Connector connector, boolean secure) {
			this.connector = connector;
			this.secure = secure;
			
			connector.receiving(new Receiver() {
				@Override
				public void received(Address address, ByteBuffer buffer) {
					if (receiver != null) {
						receiver.received(address, buffer);
					}
				}
			});
			
			connector.closing(new Closing() {
				@Override
				public void closed() {
					if (closing != null) {
						closing.closed();
					}
				}
			});
		}
	}
	
	private final Map<Long, ReusableConnector> reusableConnectors = new HashMap<>();
	private long nextReusableConnectorId = 0L;
	
	public HttpClient() {
	}

	public HttpClient with(Executor executor) {
		this.executor = executor;
		return this;
	}
	
	public HttpClient with(ConnectorFactory connectorFactory) {
		this.connectorFactory = connectorFactory;
		return this;
	}
	public HttpClient withSecure(ConnectorFactory secureConnectorFactory) {
		this.secureConnectorFactory = secureConnectorFactory;
		return this;
	}

	@Override
	public void close() {
		final Executor thisExecutor = executor;

		thisExecutor.execute(new Runnable() {
			@Override
			public void run() {
				for (ReusableConnector connector : reusableConnectors.values()) {
					connector.connector.disconnect();
				}
				reusableConnectors.clear();
			}
		});
	}


	public SnmpRequest request() {
		final Executor thisExecutor = executor;
		
		return new SnmpRequest() {
			private SnmpReceiver receiver = null;
			private Failing failing = null;

			private long id = -1L;
			private ReusableConnector connector = null;

			@Override
			public SnmpRequest receiving(SnmpReceiver receiver) {
				this.receiver = receiver;
				return this;
			}
			@Override
			public SnmpRequest failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public Send create(final HttpRequest request) {
				final SnmpReceiver r = receiver;
				final Failing f = failing;
				
				//TODO handle request Connection:close
				
				thisExecutor.execute(new Runnable() {
					@Override
					public void run() {
						if (id >= 0L) {
							throw new IllegalStateException("Could be created twice");
						}
						
						for (Map.Entry<Long, ReusableConnector> e : reusableConnectors.entrySet()) {
							long reusedId = e.getKey();
							ReusableConnector reusedConnector = e.getValue();
							if (reusedConnector.reusable && (reusedConnector.secure == request.secure)) {
								id = reusedId;
								
								connector = reusedConnector;
								
								final long thisId = id;
								connector.closing = new Closing() {
									@Override
									public void closed() {
										reusableConnectors.remove(thisId);

										try {
											parseClosed(r);
										} catch (IOException ioe) {
											LOGGER.error("Connection error", ioe);
											connector.connector.disconnect();
											reusableConnectors.remove(id);
											connector = null;
											f.failed(ioe);
										}

										connector = null;
									}
								};

								connector.receiver = new Receiver() {
									@Override
									public void received(final Address address, final ByteBuffer buffer) {
										thisExecutor.execute(new Runnable() {
											@Override
											public void run() {
												try {
													parse(buffer, r);
												} catch (IOException ioe) {
													LOGGER.error("Connection error", ioe);
													connector.connector.disconnect();
													reusableConnectors.remove(id);
													connector = null;
													f.failed(ioe);
												}
											}
										});
									}
								};
								
								connector.connector.send(null, request.toByteBuffer());
								return;
							}
						}

						id = nextReusableConnectorId;
						nextReusableConnectorId++;
						
						connector = new ReusableConnector(request.secure ? secureConnectorFactory.create() : connectorFactory.create(), request.secure);
						reusableConnectors.put(id, connector);

						final long thisId = id;
						connector.closing = new Closing() {
							@Override
							public void closed() {
								reusableConnectors.remove(thisId);

								try {
									parseClosed(r);
								} catch (IOException ioe) {
									LOGGER.error("Connection error", ioe);
									connector.connector.disconnect();
									reusableConnectors.remove(id);
									connector = null;
									f.failed(ioe);
								}

								connector = null;
							}
						};
						
						connector.receiver = new Receiver() {
							@Override
							public void received(final Address address, final ByteBuffer buffer) {
								thisExecutor.execute(new Runnable() {
									@Override
									public void run() {
										try {
											parse(buffer, r);
										} catch (IOException ioe) {
											LOGGER.error("Connection error", ioe);
											connector.connector.disconnect();
											reusableConnectors.remove(id);
											connector = null;
											f.failed(ioe);
										}
									}
								});
							}
						};
						
						connector.connector.connect();
						LOGGER.debug("Sending request: {}", request);
						connector.connector.send(null, request.toByteBuffer());
					}
				});

				//TODO Content-Encoding gzip??
				return new Send() {
					@Override
					public void post(final ByteBuffer buffer) {
						thisExecutor.execute(new Runnable() {
							@Override
							public void run() {
								if (connector != null) {
									connector.connector.send(null, buffer);
								}
							}
						});
					}
					
					@Override
					public void finish() {
						thisExecutor.execute(new Runnable() {
							@Override
							public void run() {
								if (connector != null) {
									connector.reusable = true;
									connector = null;
								}
							}
						});
					}

					@Override
					public void cancel() {
						thisExecutor.execute(new Runnable() {
							@Override
							public void run() {
								if (connector != null) {
									connector.connector.disconnect();
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

			private int responseCode;
			private String responseReason;
			private final Multimap<String, String> headers = HashMultimap.create();
			private boolean chunked;
			private long contentLength;
			private GzipReader gzipReader;

			private boolean chunkHeaderRead = false;
			private boolean chunkFooterRead = true;
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
					//%% http11 = false;
				} else if (responseVersion.equals(HttpSpecification.HTTP11)) {
					//%% http11 = true;
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
		
			private void parse(ByteBuffer buffer, final SnmpReceiver receiver) throws IOException {
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
						
						contentLength = -1L;
						
						for (String contentLengthValue : headers.get(HttpHeaderKey.CONTENT_LENGTH)) {
							try {
								contentLength = Long.parseLong(contentLengthValue);
								break;
							} catch (NumberFormatException e) {
								throw new IOException("Invalid Content-Length: " + contentLengthValue);
							}
						}
						
						gzipReader = null;
						
						for (String contentEncodingValue : headers.get(HttpHeaderKey.CONTENT_ENCODING)) {
							if (contentEncodingValue.equalsIgnoreCase(HttpHeaderValue.GZIP)) {
								gzipReader = new GzipReader();
							}
							break;
						}
						
						chunked = false;
						
						for (String transferEncodingValue : headers.get(HttpHeaderKey.TRANSFER_ENCODING)) {
							chunked = transferEncodingValue.equalsIgnoreCase(HttpHeaderValue.CHUNKED);
							break;
						}
		
						/*%%
						keepAlive = http11;
						for (String connectionValue : headers.get(HttpHeaderKey.CONNECTION)) {
							keepAlive = false;
							if (connectionValue.equalsIgnoreCase(HttpHeaderValue.KEEP_ALIVE)) {
								keepAlive = true;
							}
							break;
						}
						LOGGER.trace("Keep alive = {}", keepAlive);
						*/
						
						receiver.received(new HttpResponse(responseCode, responseReason, ImmutableMultimap.copyOf(headers)));
						
						countRead = 0;
					} else {
						LOGGER.trace("Header line: {}", line);
						addHeader(line);
					}
				}
			
				if (chunked) {
					
					while (true) {
					
						while (!chunkFooterRead) {
							String line = lineReader.handle(buffer);
							if (line == null) {
								return;
							}
							if (!line.isEmpty()) {
								throw new IOException("Invalid chunk footer");
							}
							chunkFooterRead = true;
							chunkCountRead = 0;
							if (chunkLength == 0) {
								receiver.ended();
							} else {
								chunkHeaderRead = false;
							}
						}
		
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
						}
						
						if (chunkCountRead < chunkLength) {
							long totalToRead = contentLength - countRead;
							long toRead = chunkLength - chunkCountRead;
							handleContent(buffer, toRead, totalToRead, receiver);
						}
							
						if (chunkCountRead == chunkLength) {
							if (chunkLength == 0) {
								if (buffer.hasRemaining()) {
									throw new IOException("Connection reset, too much data in chunked stream");
								}
							}
								
							chunkFooterRead = false;
						}
					
					}
		
				} else {
				
					if (contentLength >= 0) {
						if (countRead < contentLength) {
							long toRead = contentLength - countRead;
							handleContent(buffer, toRead, toRead, receiver);
						}
						if (countRead == contentLength) {
							if (buffer.hasRemaining()) {
								throw new IOException("Connection reset, too much data");
							}
							receiver.ended();
						}
					} else {
						handleContent(buffer, -1, -1, receiver);
					}
		
				}
			}
			
		    private void handleContent(ByteBuffer buffer, long toRead, long totalRemainingToRead, final SnmpReceiver receiver) throws IOException {
		    	ByteBuffer deflated = buffer.duplicate(); // We must duplicated this buffer because it can be used later by the user, but must be continued in the handle loop
		    	if (toRead >= 0) {
					if (deflated.remaining() > toRead) {
						deflated.limit((int) (buffer.position() + toRead));
					}
		    	}
		    	
		    	chunkCountRead += deflated.remaining();
				countRead += deflated.remaining();
				buffer.position((int) (buffer.position() + deflated.remaining()));
		
				if (gzipReader == null) {
					receiver.received(deflated);
					return;
				}
				
				gzipReader.handle(deflated, totalRemainingToRead, new ByteBufferHandler() {
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						receiver.received(buffer);
					}
				});
		    }
		    
			private void parseClosed(SnmpReceiver receiver) throws IOException {
				if (chunked) {
					if (!chunkFooterRead) {
						throw new IOException("Connection reset by peer in chunked stream");
					}
					receiver.ended();
				} else {
					if (contentLength < 0L) {
						receiver.ended();
						return;
					}
	
					if (countRead < contentLength) {
						throw new IOException("Connection reset by peer, expecting more data");
					}

					// receiver.ended(); // Already sent
				}
			}
		};
	}
}
