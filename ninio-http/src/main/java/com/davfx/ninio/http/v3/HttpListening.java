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
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.Listening;
import com.davfx.ninio.core.v3.Receiver;
import com.davfx.ninio.core.v3.SocketBuilder;
import com.davfx.ninio.http.HttpHeaderKey;
import com.davfx.ninio.http.HttpHeaderValue;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
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
	private final ByteBuffer emptyLineByteBuffer = LineReader.toBuffer("");

	private final Map<String, String> headerSanitization = new HashMap<String, String>();
	
	{
		headerSanitization.put(HttpHeaderKey.CONTENT_LENGTH.toLowerCase(), HttpHeaderKey.CONTENT_LENGTH);
		headerSanitization.put(HttpHeaderKey.CONTENT_ENCODING.toLowerCase(), HttpHeaderKey.CONTENT_ENCODING);
		headerSanitization.put(HttpHeaderKey.TRANSFER_ENCODING.toLowerCase(), HttpHeaderKey.TRANSFER_ENCODING);
		headerSanitization.put(HttpHeaderKey.CONNECTION.toLowerCase(), HttpHeaderKey.CONNECTION);
		headerSanitization.put(HttpHeaderKey.ACCEPT_ENCODING.toLowerCase(), HttpHeaderKey.ACCEPT_ENCODING);
	}
	
	private HttpListening(Executor executor, boolean secure, HttpListeningHandler listeningHandler) {
		this.executor = executor;
		this.secure = secure;
		this.listeningHandler = listeningHandler;
	}

	@Override
	public void connecting(final Address from, final Connector connectingConnector, SocketBuilder<?> builder) {
		LOGGER.debug("Connecting from {}", from);
		
		if (listeningHandler == null) {
			connectingConnector.close();
			return;
		}
		
		final HttpListeningHandler.ConnectionHandler connectionHandler = listeningHandler.create();
		
		builder.closing(connectionHandler);
		
		final Failing failing = new Failing() {
			@Override
			public void failed(IOException e) {
				LOGGER.error("Error", e);
				connectingConnector.close();
			}
		};
		
		builder.receiving(new Receiver() {
			
			private final LineReader lineReader = new LineReader();
			private boolean requestLineRead = false;
			private boolean requestHeadersRead;
			private HttpMethod requestMethod;
			private String requestPath;
			private HttpVersion requestVersion;
			private boolean requestKeepAlive;
			private boolean requestAcceptGzip;
			private final Multimap<String, String> requestHeaders = HashMultimap.create();
			
			private HttpContentReceiver handler;

			private boolean addHeader(String headerLine) {
				int i = headerLine.indexOf(HttpSpecification.HEADER_KEY_VALUE_SEPARATOR);
				if (i < 0) {
					failing.failed(new IOException("Invalid header: " + headerLine));
					return false;
				}
				String key = headerLine.substring(0, i);
				String sanitizedKey = headerSanitization.get(key.toLowerCase());
				if (sanitizedKey != null) {
					key = sanitizedKey;
				}
				String value = headerLine.substring(i + 1).trim();
				requestHeaders.put(key, value);
				return true;
			}
			
			private boolean setRequestLine(String requestLine) {
				int i = requestLine.indexOf(HttpSpecification.START_LINE_SEPARATOR);
				if (i < 0) {
					failing.failed(new IOException("Invalid request: " + requestLine));
					return false;
				}
				int j = requestLine.indexOf(HttpSpecification.START_LINE_SEPARATOR, i + 1);
				if (j < 0) {
					failing.failed(new IOException("Invalid request: " + requestLine));
					return false;
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
					failing.failed(new IOException("Invalid request: " + requestLine));
					return false;
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
				
				String version = requestLine.substring(j + 1);
				if (version.startsWith(HttpSpecification.HTTP_VERSION_PREFIX)) {
					failing.failed(new IOException("Unsupported version: " + version));
					return false;
				}
				version = version.substring(HttpSpecification.HTTP_VERSION_PREFIX.length());
				if (version.equals(HttpVersion.HTTP10.toString())) {
					requestVersion = HttpVersion.HTTP10;
				} else if (version.equals(HttpVersion.HTTP11.toString())) {
					requestVersion = HttpVersion.HTTP11;
				} else {
					failing.failed(new IOException("Unsupported version: " + version));
					return false;
				}
				return true;
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
						if (!setRequestLine(line)) {
							return;
						}
						requestLineRead = true;
						requestHeadersRead = false;
					}
			
					while (!requestHeadersRead) {
						String line = lineReader.handle(buffer);
						if (line == null) {
							return;
						}
						LOGGER.debug("Header line: {}", line);
						if (line.isEmpty()) {
							requestHeadersRead = true;

							final HttpContentReceiver h = connectionHandler.handle(new HttpRequest(from, secure, requestMethod, requestPath, ImmutableMultimap.copyOf(requestHeaders)), new HttpListeningHandler.ConnectionHandler.ResponseHandler() {
								private boolean responseKeepAlive;
								private HttpContentSender sender = null;
								
								@Override
								public HttpContentSender send(final HttpResponse response) {
									executor.execute(new Runnable() {
										@Override
										public void run() {
											if (sender != null) {
												LOGGER.error("Could not send a response multiple times");
												return;
											}
											
											Multimap<String, String> completedHeaders = ArrayListMultimap.create(response.headers);
											if (requestAcceptGzip && !completedHeaders.containsKey(HttpHeaderKey.CONTENT_ENCODING)) {
												completedHeaders.put(HttpHeaderKey.CONTENT_ENCODING, HttpHeaderValue.GZIP);
											}
											if (!completedHeaders.containsKey(HttpHeaderKey.CONNECTION)) {
												completedHeaders.put(HttpHeaderKey.CONNECTION, requestKeepAlive ? HttpHeaderValue.KEEP_ALIVE :  HttpHeaderValue.CLOSE);
											}

											responseKeepAlive = (requestVersion != HttpVersion.HTTP10);
											for (String connectionValue : completedHeaders.get(HttpHeaderKey.CONNECTION)) {
												if (connectionValue.equalsIgnoreCase(HttpHeaderValue.CLOSE)) {
													responseKeepAlive = false;
												} else if (connectionValue.equalsIgnoreCase(HttpHeaderValue.KEEP_ALIVE)) {
													responseKeepAlive = true;
												}
											}
											
											if (responseKeepAlive && !completedHeaders.containsKey(HttpHeaderKey.CONTENT_LENGTH) && !completedHeaders.containsKey(HttpHeaderKey.TRANSFER_ENCODING)) {
												completedHeaders.put(HttpHeaderKey.TRANSFER_ENCODING, HttpHeaderValue.CHUNKED);
											}

											for (String contentLengthValue : completedHeaders.get(HttpHeaderKey.CONTENT_LENGTH)) {
												try {
													long responseContentLength = Long.parseLong(contentLengthValue);
													sender = new ContentLengthWriter(responseContentLength, sender);
												} catch (NumberFormatException e) {
													LOGGER.error("Invalid Content-Length: {}", contentLengthValue);
												}
												break;
											}
											
											for (String contentEncodingValue : completedHeaders.get(HttpHeaderKey.CONTENT_ENCODING)) {
												if (contentEncodingValue.equalsIgnoreCase(HttpHeaderValue.GZIP)) {
													sender = new GzipWriter(sender);
												}
												break;
											}
											
											for (String transferEncodingValue : completedHeaders.get(HttpHeaderKey.TRANSFER_ENCODING)) {
												if (transferEncodingValue.equalsIgnoreCase(HttpHeaderValue.CHUNKED)) {
													sender = new ChunkedWriter(sender);
												}
												break;
											}
							
											connector.send(null, LineReader.toBuffer(requestVersion.toString() + HttpSpecification.START_LINE_SEPARATOR + response.status + HttpSpecification.START_LINE_SEPARATOR + response.reason));

											for (Map.Entry<String, String> h : completedHeaders.entries()) {
												String k = h.getKey();
												String v = h.getValue();
												connector.send(null, LineReader.toBuffer(k + HttpSpecification.HEADER_KEY_VALUE_SEPARATOR + HttpSpecification.HEADER_BEFORE_VALUE + v));
											}
											connector.send(null, emptyLineByteBuffer.duplicate());
										}
									});

									return new HttpContentSender() {
										@Override
										public void cancel() {
											executor.execute(new Runnable() {
												@Override
												public void run() {
													if (sender == null) {
														return;
													}
													
													sender.cancel();
													connector.close();
												}
											});
										}
										
										@Override
										public HttpContentSender send(final ByteBuffer buffer) {
											executor.execute(new Runnable() {
												@Override
												public void run() {
													if (sender == null) {
														return;
													}
													
													sender.send(buffer);
												}
											});
											return this;
										}
										
										@Override
										public void finish() {
											executor.execute(new Runnable() {
												@Override
												public void run() {
													if (sender == null) {
														return;
													}
													
													sender.finish();

													requestLineRead = false;
													holding = false;
													sender = null;
													handler = null;
													
													if (responseKeepAlive) {
														continueReceived(connector);
													} else {
														LOGGER.debug("Actually closed");
														connector.close();
													}
												}
											});
										}
									};
								}
							});
							
							handler = new HttpContentReceiver() {
								@Override
								public void received(ByteBuffer buffer) {
									h.received(buffer);
								}
								@Override
								public void ended() {
									h.ended();
									holding = true;
								}
							};
							
							for (String contentLengthValue : requestHeaders.get(HttpHeaderKey.CONTENT_LENGTH)) {
								try {
									long headerContentLength = Long.parseLong(contentLengthValue);
									handler = new ContentLengthReader(headerContentLength, failing, handler);
								} catch (NumberFormatException e) {
									LOGGER.error("Invalid Content-Length: {}", contentLengthValue);
								}
								break;
							}
							
							for (String contentEncodingValue : requestHeaders.get(HttpHeaderKey.CONTENT_ENCODING)) {
								if (contentEncodingValue.equalsIgnoreCase(HttpHeaderValue.GZIP)) {
									handler = new GzipReader(failing, handler);
								}
								break;
							}
							
							for (String transferEncodingValue : requestHeaders.get(HttpHeaderKey.TRANSFER_ENCODING)) {
								if (transferEncodingValue.equalsIgnoreCase(HttpHeaderValue.CHUNKED)) {
									handler = new ChunkedReader(failing, handler);
								}
							}
			
							boolean headerKeepAlive = true;
							for (String connectionValue : requestHeaders.get(HttpHeaderKey.CONNECTION)) {
								if (connectionValue.equalsIgnoreCase(HttpHeaderValue.CLOSE)) {
									headerKeepAlive = false;
								} else if (connectionValue.equalsIgnoreCase(HttpHeaderValue.KEEP_ALIVE)) {
									headerKeepAlive = true;
								}
							}
							requestKeepAlive = headerKeepAlive;
							
							boolean headerAcceptGzip = false;
							for (String accept : requestHeaders.get(HttpHeaderKey.ACCEPT_ENCODING)) {
								for (String a : Splitter.on(',').splitToList(accept)) {
									if (a.trim().equalsIgnoreCase(HttpHeaderValue.GZIP)) {
										headerAcceptGzip = accept.contains(HttpHeaderValue.GZIP);
										break;
									}
								}
								if (headerAcceptGzip) {
									break;
								}
							}
							requestAcceptGzip = headerAcceptGzip;
							
						} else {
							if (!addHeader(line)) {
								return;
							}
						}
					}

					if (handler != null) {
						handler.received(buffer);
					}
				}
			}
		});
	}
}
