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
import com.davfx.ninio.core.Listener;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

public final class HttpListening implements Listener.Callback.Connecting {
	
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
				if (handler == null) {
					throw new NullPointerException("handler");
				}
				return new HttpListening(executor, secure, handler);
			}
		};
	}
	

	private final Executor executor;
	private final boolean secure;
	private final HttpListeningHandler listeningHandler;
	
	private Address from = null;
	private Connecter.Connecting connecting = null;
	
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
	public void closed() {
		listeningHandler.closed();
	}

	@Override
	public void connected(Address address) {
		LOGGER.debug("Connecting from {}", address);
		from = address;
	}
	
	@Override
	public void failed(IOException ioe) {
		listeningHandler.failed(ioe);
	}

	private void abruptlyCloseAndFail(IOException ioe) {
		connecting.close();
		listeningHandler.failed(ioe);
	}
	
	//
	
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
			abruptlyCloseAndFail(new IOException("Invalid header: " + headerLine));
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
			abruptlyCloseAndFail(new IOException("Invalid request: " + requestLine));
			return false;
		}
		int j = requestLine.indexOf(HttpSpecification.START_LINE_SEPARATOR, i + 1);
		if (j < 0) {
			abruptlyCloseAndFail(new IOException("Invalid request: " + requestLine));
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
			abruptlyCloseAndFail(new IOException("Invalid request: " + requestLine));
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
		if (!version.startsWith(HttpSpecification.HTTP_VERSION_PREFIX)) {
			abruptlyCloseAndFail(new IOException("Unsupported version: " + version));
			return false;
		}
		version = version.substring(HttpSpecification.HTTP_VERSION_PREFIX.length());
		if (version.equals(HttpVersion.HTTP10.toString())) {
			requestVersion = HttpVersion.HTTP10;
		} else if (version.equals(HttpVersion.HTTP11.toString())) {
			requestVersion = HttpVersion.HTTP11;
		} else {
			abruptlyCloseAndFail(new IOException("Unsupported version: " + version));
			return false;
		}
		return true;
	}
	
	private final Deque<ByteBuffer> hold = new LinkedList<>();
	private boolean holding = false;
	private boolean closed = false;
	
	@Override
	public void received(Address address, final ByteBuffer buffer) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				hold.addLast(buffer);
				continueReceived();
			}
		});
	}
	
	private void continueReceived() {
		while (true) {
			if (holding) {
				return;
			}
			if (hold.isEmpty()) {
				return;
			}
			ByteBuffer buffer = hold.removeFirst();
			handleReceived(buffer);
		}
	}
	
	private void handleReceived(ByteBuffer buffer) {
		while (buffer.hasRemaining()) {
			if (closed) {
				abruptlyCloseAndFail(new IOException("Could not receive more"));
				return;
			}
			if (holding) {
				LOGGER.trace("Holding packet: {} bytes", buffer.remaining());
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

					final HttpContentReceiver h = listeningHandler.handle(new HttpRequest(from, secure, requestMethod, requestPath, ImmutableMultimap.copyOf(requestHeaders)), new HttpListeningHandler.ResponseHandler() {
						private boolean responseKeepAlive;
						private HttpContentSender sender = null;
						
						@Override
						public HttpContentSender send(final HttpResponse response) {
							executor.execute(new Runnable() {
								@Override
								public void run() {
									LOGGER.debug("Sending response: {}", response);

									if (sender != null) {
										LOGGER.error("Could not send a response multiple times");
										return;
									}
									
									sender = new HttpContentSender() {
										@Override
										public void send(ByteBuffer buffer, Connecter.Connecting.Callback callback) {
											connecting.send(null, buffer, callback);
										}
										
										@Override
										public void finish() {
											LOGGER.debug("Response finished");
											requestLineRead = false;
											holding = false;
											sender = null;
											handler = null;
											
											if (responseKeepAlive) {
												continueReceived();
											} else {
												LOGGER.debug("Actually closed");
												closed = true;
												// Will be closed by client // connector.close();
											}
										}
										
										@Override
										public void cancel() {
											connecting.close();
										}
									};
									
									Multimap<String, String> completedHeaders = ArrayListMultimap.create(response.headers);

									responseKeepAlive = (requestVersion != HttpVersion.HTTP10);
									boolean automaticallySetGzipChunked = responseKeepAlive;
									for (String connectionValue : completedHeaders.get(HttpHeaderKey.CONNECTION)) {
										if (connectionValue.equalsIgnoreCase(HttpHeaderValue.CLOSE)) {
											responseKeepAlive = false;
										} else if (connectionValue.equalsIgnoreCase(HttpHeaderValue.KEEP_ALIVE)) {
											responseKeepAlive = true;
										} else {
											automaticallySetGzipChunked = false;
										}
									}

									if (!completedHeaders.containsKey(HttpHeaderKey.CONNECTION)) {
										responseKeepAlive = requestKeepAlive;
										completedHeaders.put(HttpHeaderKey.CONNECTION, responseKeepAlive ? HttpHeaderValue.KEEP_ALIVE :  HttpHeaderValue.CLOSE);
									}

									if (automaticallySetGzipChunked && requestAcceptGzip && !completedHeaders.containsKey(HttpHeaderKey.CONTENT_ENCODING) && !completedHeaders.containsKey(HttpHeaderKey.CONTENT_LENGTH)) { // Content-Length MUST refer to the compressed data length, which the user is not aware of, thus we CANNOT compress if the user specifies a Content-Length
										completedHeaders.put(HttpHeaderKey.CONTENT_ENCODING, HttpHeaderValue.GZIP);
									}
									if (automaticallySetGzipChunked && !completedHeaders.containsKey(HttpHeaderKey.CONTENT_LENGTH) && !completedHeaders.containsKey(HttpHeaderKey.TRANSFER_ENCODING)) {
										completedHeaders.put(HttpHeaderKey.TRANSFER_ENCODING, HttpHeaderValue.CHUNKED);
									}

									for (String transferEncodingValue : completedHeaders.get(HttpHeaderKey.TRANSFER_ENCODING)) {
										if (transferEncodingValue.equalsIgnoreCase(HttpHeaderValue.CHUNKED)) {
											LOGGER.debug("Response is chunked");
											sender = new ChunkedWriter(sender);
										}
										break;
									}
					
									for (String contentLengthValue : completedHeaders.get(HttpHeaderKey.CONTENT_LENGTH)) {
										try {
											long responseContentLength = Long.parseLong(contentLengthValue);
											LOGGER.debug("Response content length: {}", responseContentLength);
											sender = new ContentLengthWriter(responseContentLength, sender);
										} catch (NumberFormatException e) {
											LOGGER.error("Invalid Content-Length: {}", contentLengthValue);
										}
										break;
									}
									
									for (String contentEncodingValue : completedHeaders.get(HttpHeaderKey.CONTENT_ENCODING)) {
										if (contentEncodingValue.equalsIgnoreCase(HttpHeaderValue.GZIP)) {
											LOGGER.debug("Response is gzip");
											sender = new GzipWriter(sender);
										}
										break;
									}
									
									Connecter.Connecting.Callback sendCallback = new Connecter.Connecting.Callback() {
										@Override
										public void sent() {
										}
										@Override
										public void failed(IOException ioe) {
											abruptlyCloseAndFail(ioe);
										}
									};
									
									connecting.send(null, LineReader.toBuffer(HttpSpecification.HTTP_VERSION_PREFIX + requestVersion.toString() + HttpSpecification.START_LINE_SEPARATOR + response.status + HttpSpecification.START_LINE_SEPARATOR + response.reason), sendCallback);

									LOGGER.debug("Response headers sent: {}", completedHeaders);
									for (Map.Entry<String, String> h : completedHeaders.entries()) {
										String k = h.getKey();
										String v = h.getValue();
										connecting.send(null, LineReader.toBuffer(k + HttpSpecification.HEADER_KEY_VALUE_SEPARATOR + HttpSpecification.HEADER_BEFORE_VALUE + v), sendCallback);
									}
									connecting.send(null, emptyLineByteBuffer.duplicate(), sendCallback);
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
										}
									});
								}
								
								@Override
								public void send(final ByteBuffer buffer, final Connecter.Connecting.Callback callback) {
									executor.execute(new Runnable() {
										@Override
										public void run() {
											if (sender == null) {
												return;
											}
											
											sender.send(buffer, callback);
										}
									});
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
										}
									});
								}
							};
						}
					});
					
					handler = new HttpContentReceiver() {
						@Override
						public void received(ByteBuffer buffer) {
							ByteBuffer b = buffer.duplicate();
							buffer.position(buffer.position() + buffer.remaining());
							if (h != null) {
								h.received(b);
							}
						}
						@Override
						public void ended() {
							if (h != null) {
								h.ended();
							}
							holding = true;
						}
					};
					
					boolean headerKeepAlive = (requestVersion == HttpVersion.HTTP11);
					boolean automaticallySetContentLength = headerKeepAlive;
					for (String connectionValue : requestHeaders.get(HttpHeaderKey.CONNECTION)) {
						if (connectionValue.equalsIgnoreCase(HttpHeaderValue.CLOSE)) {
							headerKeepAlive = false;
						} else if (connectionValue.equalsIgnoreCase(HttpHeaderValue.KEEP_ALIVE)) {
							headerKeepAlive = true;
						} else {
							automaticallySetContentLength = false;
						}
					}
					requestKeepAlive = headerKeepAlive;
					LOGGER.debug("Request keep alive: {}", requestKeepAlive);
					
					if (automaticallySetContentLength && !requestHeaders.containsKey(HttpHeaderKey.CONTENT_LENGTH) && !requestHeaders.containsKey(HttpHeaderKey.TRANSFER_ENCODING)) {
						requestHeaders.put(HttpHeaderKey.CONTENT_LENGTH, String.valueOf(0L));
					}

					ReaderFailing failing = new ReaderFailing() {
						@Override
						public void failed(IOException ioe) {
							abruptlyCloseAndFail(ioe);
						}
					};
					
					for (String contentEncodingValue : requestHeaders.get(HttpHeaderKey.CONTENT_ENCODING)) {
						if (contentEncodingValue.equalsIgnoreCase(HttpHeaderValue.GZIP)) {
							LOGGER.debug("Request is gzip");
							handler = new GzipReader(failing, handler);
						}
						break;
					}
					
					for (String contentLengthValue : requestHeaders.get(HttpHeaderKey.CONTENT_LENGTH)) {
						try {
							long headerContentLength = Long.parseLong(contentLengthValue);
							LOGGER.debug("Request content length: {}", headerContentLength);
							handler = new ContentLengthReader(headerContentLength, failing, handler);
						} catch (NumberFormatException e) {
							LOGGER.error("Invalid Content-Length: {}", contentLengthValue);
						}
						break;
					}
					
					for (String transferEncodingValue : requestHeaders.get(HttpHeaderKey.TRANSFER_ENCODING)) {
						if (transferEncodingValue.equalsIgnoreCase(HttpHeaderValue.CHUNKED)) {
							LOGGER.debug("Request is chunked");
							handler = new ChunkedReader(failing, handler);
						}
					}
	
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
					LOGGER.debug("Request accept gzip: {}", requestAcceptGzip);

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

	@Override
	public void connecting(Connecter.Connecting connecting) {
		this.connecting = connecting;
	}
}
