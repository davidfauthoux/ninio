package com.davfx.ninio.http.v3;

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
import com.davfx.ninio.core.ByteBufferHandler;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Listening;
import com.davfx.ninio.core.v3.Receiver;
import com.davfx.ninio.core.v3.SocketBuilder;
import com.davfx.ninio.http.HttpHeaderKey;
import com.davfx.ninio.http.HttpHeaderValue;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpSpecification;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

public final class HttpListening implements Listening {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpListening.class);
	
	public static interface Builder {
		Builder secure();
		Builder with(Executor executor);
		Builder with(HttpListeningHandler handler);
		HttpListening build();
	}
	
	public static Builder builder() {
		return new Builder() {
			private boolean secure = false;
			private Executor executor = null;
			private HttpListeningHandler handler = null;
			
			@Override
			public Builder secure() {
				secure = true;
				return this;
			}
			
			@Override
			public Builder with(Executor executor) {
				this.executor = executor;
				return this;
			}
			
			@Override
			public Builder with(HttpListeningHandler handler) {
				this.handler = handler;
				return this;
			}

			@Override
			public HttpListening build() {
				if (executor == null) {
					throw new NullPointerException("executor");
				}
				return new HttpListening(executor, secure, handler);
			}
		};
	}
	

	private final Executor executor;
	private final boolean secure;
	private final HttpListeningHandler listeningHandler;

	// Optim (not static because ByteBuffer.duplicate could be thread-unsafe)
	private final ByteBuffer gzipHeaderByteBuffer = LineReader.toBuffer(HttpHeaderKey.CONTENT_ENCODING + HttpSpecification.HEADER_KEY_VALUE_SEPARATOR + HttpSpecification.HEADER_BEFORE_VALUE + HttpHeaderValue.GZIP.toString());
	private final ByteBuffer chunkedHeaderByteBuffer = LineReader.toBuffer(HttpHeaderKey.TRANSFER_ENCODING + HttpSpecification.HEADER_KEY_VALUE_SEPARATOR + HttpSpecification.HEADER_BEFORE_VALUE + HttpHeaderValue.CHUNKED.toString());
	private final ByteBuffer emptyLineByteBuffer = LineReader.toBuffer("");
	private final ByteBuffer zeroByteBuffer = LineReader.toBuffer(Integer.toHexString(0));

	private HttpListening(Executor executor, boolean secure, HttpListeningHandler listeningHandler) {
		this.executor = executor;
		this.secure = secure;
		this.listeningHandler = listeningHandler;
	}

	@Override
	public void connecting(final Address from, Connector connector, SocketBuilder<?> builder) {
		LOGGER.debug("Connecting from {}", from);
		
		if (listeningHandler == null) {
			connector.close();
			return;
		}
		
		final HttpListeningHandler.ConnectionHandler connectionHandler = listeningHandler.create();
		
		builder.closing(connectionHandler);
		
		builder.receiving(new Receiver() {
			
			private final LineReader lineReader = new LineReader();
			private boolean headersRead = false;
			private boolean requestLineRead = false;
			private long contentLength;
			private int countRead;
			private HttpMethod requestMethod;
			private String requestPath;
			private boolean http11;
			private boolean enableGzip = false;
			private final Multimap<String, String> headers = HashMultimap.create();
			
			private HttpListeningHandler.ConnectionHandler.RequestHandler handler;

			private final Map<String, String> headerSanitization = new HashMap<String, String>();
			
			{
				headerSanitization.put(HttpHeaderKey.CONTENT_LENGTH.toLowerCase(), HttpHeaderKey.CONTENT_LENGTH);
				headerSanitization.put(HttpHeaderKey.CONTENT_ENCODING.toLowerCase(), HttpHeaderKey.CONTENT_ENCODING);
				headerSanitization.put(HttpHeaderKey.CONTENT_TYPE.toLowerCase(), HttpHeaderKey.CONTENT_TYPE);
				headerSanitization.put(HttpHeaderKey.ACCEPT_ENCODING.toLowerCase(), HttpHeaderKey.ACCEPT_ENCODING);
				headerSanitization.put(HttpHeaderKey.TRANSFER_ENCODING.toLowerCase(), HttpHeaderKey.TRANSFER_ENCODING);
			}
			
			private void addHeader(String headerLine) throws IOException {
				int i = headerLine.indexOf(HttpSpecification.HEADER_KEY_VALUE_SEPARATOR);
				if (i < 0) {
					throw new IOException("Invalid header: " + headerLine);
				}
				String key = headerLine.substring(0, i);
				String sanitizedKey = headerSanitization.get(key.toLowerCase());
				if (sanitizedKey != null) {
					key = sanitizedKey;
				}
				String value = headerLine.substring(i + 1).trim();
				headers.put(key, value);
			}
			private void setRequestLine(String requestLine) throws IOException {
				int i = requestLine.indexOf(HttpSpecification.START_LINE_SEPARATOR);
				if (i < 0) {
					throw new IOException("Invalid request: " + requestLine);
				}
				int j = requestLine.indexOf(HttpSpecification.START_LINE_SEPARATOR, i + 1);
				if (j < 0) {
					throw new IOException("Invalid request: " + requestLine);
				}
				requestMethod = null;
				String m = requestLine.substring(0, i);
				for (HttpMethod method : HttpMethod.values()) {
					if (method.toString().equals(m)) {
						requestMethod = method;
						break;
					}
				}
				if (requestMethod == null) {
					throw new IOException("Invalid request: " + requestLine);
				}
				requestPath = requestLine.substring(i + 1, j);
				
				/*
				// Tolerance
				if (requestPath.isEmpty()) {
					requestPath = String.valueOf(HttpSpecification.PATH_SEPARATOR);
				} else if (requestPath.charAt(0) != HttpSpecification.PATH_SEPARATOR) {
					requestPath = HttpSpecification.PATH_SEPARATOR + requestPath;
				}
				*/
				
				String requestVersion = requestLine.substring(j + 1);
				if (requestVersion.equals(HttpSpecification.HTTP10)) {
					http11 = false;
				} else if (requestVersion.equals(HttpSpecification.HTTP11)) {
					http11 = true;
				} else {
					throw new IOException("Unsupported version");
				}
			}
			
			private final Deque<ByteBuffer> hold = new LinkedList<>();
			private boolean holding = false;
			
			@Override
			public void received(final Connector connector, Address address, final ByteBuffer buffer) {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						hold.addLast(buffer);
						continueReceived(connector);
					}
				});
			}
			
			private void continueReceived(Connector connector) {
				while (true) {
					if (holding) {
						return;
					}
					if (hold.isEmpty()) {
						return;
					}
					ByteBuffer buffer = hold.removeFirst();
					handleReceived(connector, buffer);
				}
			}
			
			private void handleReceived(final Connector connector, ByteBuffer buffer) {
				try {
					while (buffer.hasRemaining()) {
						if (holding) {
							hold.addLast(buffer);
							return;
						}
						while (!requestLineRead) {
							String line = lineReader.handle(buffer);
							if (line == null) {
								return;
							}
							LOGGER.debug("Request line: {}", line);
							setRequestLine(line);
							requestLineRead = true;
						}
				
						while (!headersRead) {
							String line = lineReader.handle(buffer);
							if (line == null) {
								return;
							}
							LOGGER.debug("Header line: {}", line);
							if (line.isEmpty()) {
								headersRead = true;
	
								long headerContentLength = -1L;
								for (String contentLengthValue : headers.get(HttpHeaderKey.CONTENT_LENGTH)) {
									try {
										headerContentLength = Long.parseLong(contentLengthValue);
									} catch (NumberFormatException e) {
										LOGGER.error("Invalid Content-Length: {}", contentLengthValue);
									}
								}
								final long requestContentLength = headerContentLength;
								
								GzipWriter headerGzip = null;
								for (String contentEncodingValue : headers.get(HttpHeaderKey.CONTENT_ENCODING)) {
									if (contentEncodingValue.equalsIgnoreCase(HttpHeaderValue.GZIP)) {
										headerGzip = new GzipWriter();
									}
								}
								final GzipWriter requestGzip = headerGzip;
								
								boolean headerChunked = (requestContentLength < 0L);
								for (String transferEncodingValue : headers.get(HttpHeaderKey.TRANSFER_ENCODING)) {
									headerChunked = transferEncodingValue.equalsIgnoreCase(HttpHeaderValue.CHUNKED);
								}
								final boolean requestChunked = headerChunked;
				
								boolean headerKeepAlive = true;
								for (String connectionValue : headers.get(HttpHeaderKey.CONNECTION)) {
									if (connectionValue.equalsIgnoreCase(HttpHeaderValue.CLOSE)) {
										headerKeepAlive = false;
									} else if (connectionValue.equalsIgnoreCase(HttpHeaderValue.KEEP_ALIVE)) {
										headerKeepAlive = true;
									}
								}
								final boolean requestKeepAlive = headerKeepAlive;
								

								for (String accept : headers.get(HttpHeaderKey.ACCEPT_ENCODING)) {
									for (String a : Splitter.on(',').splitToList(accept)) {
										if (a.trim().equalsIgnoreCase(HttpHeaderValue.GZIP)) {
											enableGzip = accept.contains(HttpHeaderValue.GZIP);
											break;
										}
									}
									if (enableGzip) {
										break;
									}
								}
								
								handler = connectionHandler.handle(new HttpRequest(from, secure, requestMethod, requestPath, ImmutableMultimap.copyOf(headers)), new HttpListeningHandler.ConnectionHandler.ResponseHandler() {
									private boolean sent = false;

									private GzipWriter gzipWriter;
									private boolean keepAlive;
									private boolean chunked;
									private long writeContentLength;
									private long countWrite;
									
									private void doWrite(ByteBuffer buffer) {
										if (!buffer.hasRemaining()) {
											return;
										}
										if (chunked) {
											connector.send(null, LineReader.toBuffer(Integer.toHexString(buffer.remaining())));
										}
										countWrite += buffer.remaining();
										connector.send(null, buffer);
										if (chunked) {
											connector.send(null, emptyLineByteBuffer.duplicate());
										}
									}
	
									@Override
									public ContentSender send(final HttpResponse response) {
										executor.execute(new Runnable() {
											@Override
											public void run() {
												if (sent) {
													LOGGER.error("Could not send a response multiple times");
													return;
												}
												sent = true;
												
												if (http11) {
													if (enableGzip) {
														for (String contentEncodingValue : response.headers.get(HttpHeaderKey.CONTENT_ENCODING)) {
															if (contentEncodingValue.equalsIgnoreCase(HttpHeaderValue.GZIP)) {
																gzipWriter = new GzipWriter(new ByteBufferHandler() {
																	@Override
																	public void handle(Address address, ByteBuffer buffer) {
																		doWrite(buffer);
																	}
																});
															}
														}
													}
													
													if (gzipWriter == null) {
														keepAlive = true;
														for (String connectionValue : headers.get(HttpHeaderKey.CONNECTION)) {
															if (!connectionValue.equalsIgnoreCase(HttpHeaderValue.KEEP_ALIVE)) {
																keepAlive = false;
															}
															break;
														}
			
														chunked = keepAlive;
														for (String transferEncodingValue : response.headers.get(HttpHeaderKey.TRANSFER_ENCODING)) {
															chunked = transferEncodingValue.equalsIgnoreCase(HttpHeaderValue.CHUNKED);
															break;
														}
														LOGGER.debug("No gzip, chunked = {}", chunked);
													} else {
														chunked = true;
														LOGGER.debug("Gzip, chunked = {}", chunked);
														// deflated length != source length
													}
												}
												
												for (String contentLengthValue : response.headers.get(HttpHeaderKey.CONTENT_LENGTH)) {
													try {
														writeContentLength = Long.parseLong(contentLengthValue);
													} catch (NumberFormatException nfe) {
														LOGGER.trace("Invalid content-length: {}", contentLengthValue);
														writeContentLength = 0;
													}
												}
												// Don't fallback anymore in case of no content length, because of websockets // if (writeContentLength == -1) { chunked = true; }
												
												countWrite = 0L;
												
												connector.send(null, LineReader.toBuffer((http11 ? HttpSpecification.HTTP11 : HttpSpecification.HTTP10) + HttpSpecification.START_LINE_SEPARATOR + response.status + HttpSpecification.START_LINE_SEPARATOR + response.reason));
												for (Map.Entry<String, String> h : response.headers.entries()) {
													String k = h.getKey();
													String v = h.getValue();
													if (gzipWriter != null) {
														if (k.equals(HttpHeaderKey.CONTENT_LENGTH)) {
															continue;
														}
														if (k.equals(HttpHeaderKey.TRANSFER_ENCODING)) {
															continue;
														}
													}
													if (k.equals(HttpHeaderKey.CONTENT_ENCODING)) {
														continue;
													}
													connector.send(null, LineReader.toBuffer(k + HttpSpecification.HEADER_KEY_VALUE_SEPARATOR + HttpSpecification.HEADER_BEFORE_VALUE + v));
												}
												if (gzipWriter != null) {
													connector.send(null, gzipHeaderByteBuffer.duplicate());
												}
												if (chunked) {
													connector.send(null, chunkedHeaderByteBuffer.duplicate());
												}
												connector.send(null, emptyLineByteBuffer.duplicate());
											}
										});

										return new ContentSender() {
											private boolean finished = false;
											
											@Override
											public ContentSender send(final ByteBuffer buffer) {
												executor.execute(new Runnable() {
													@Override
													public void run() {
														if (finished) {
															return;
														}
														
														if (gzipWriter != null) {
															gzipWriter.handle(buffer);
														} else {
															doWrite(buffer);
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
														if (finished) {
															return;
														}
														
														finished = true;
														
														if (gzipWriter != null) {
															gzipWriter.close();
														}
														
														if (http11) {
															if (chunked) {
																connector.send(null, zeroByteBuffer.duplicate());
																connector.send(null, emptyLineByteBuffer.duplicate());
															}
															
															if (keepAlive && (chunked || ((writeContentLength >= 0) && (countWrite == writeContentLength)))) {
																countRead = 0;
																requestLineRead = false;
																headersRead = false;
																headers.clear();
																handler = null;
																holding = false;
																continueReceived(connector);
																return;
															}
														}
														
														LOGGER.debug("Actually closed");
														connector.close();
													}
												});
											}
										};
									}
								});
	
								if (requestMethod == HttpMethod.POST) {
									contentLength = -1;
									for (String contentLengthValue : headers.get(HttpHeaderKey.CONTENT_LENGTH)) {
										try {
											contentLength = Long.parseLong(contentLengthValue);
										} catch (NumberFormatException nfe) {
											throw new IOException("Invalid Content-Length: " + contentLengthValue);
										}
										break;
									}
									if (contentLength < 0) {
										//TODO Chunked transfer coding
										throw new IOException("Content-Length required");
									}
								} else {
									contentLength = 0;
								}
								
								countRead = 0;
							} else {
								addHeader(line);
							}
						}
	
						LOGGER.debug("Content length = {}, buffer size = {}", contentLength, buffer.remaining());
	
						if (countRead < contentLength) {
							if (buffer.hasRemaining()) {
								ByteBuffer d;
								long toRead = contentLength - countRead;
								if (buffer.remaining() > toRead) {
									d = buffer.duplicate();
									d.limit((int) (buffer.position() + toRead));
									buffer.position((int) (buffer.position() + toRead));
								} else {
									d = buffer.duplicate();
									buffer.position(buffer.position() + buffer.remaining());
								}
								countRead += d.remaining();
								handler.received(d);
							}
						}
				
						if ((handler != null) && (countRead == contentLength)) {
							handler.ended();
							holding = true;
						}
					}
				} catch (IOException ioe) {
					LOGGER.error("Error", ioe);
					connector.close();
					if (handler != null) {
						handler.ended();
						handler = null;
					}
				}
			}
		});
	}
}
