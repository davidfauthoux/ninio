package com.davfx.ninio.http.util;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.http.DispatchHttpServerHandler;
import com.davfx.ninio.http.HttpContentType;
import com.davfx.ninio.http.HttpHeaderKey;
import com.davfx.ninio.http.HttpMessage;
import com.davfx.ninio.http.HttpPath;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpRequestFilter;
import com.davfx.ninio.http.HttpRequestFunctionContainer;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpServer;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;
import com.davfx.ninio.http.HttpSpecification;
import com.davfx.ninio.http.HttpStatus;
import com.davfx.ninio.http.util.HttpController.HttpAsyncOutput;
import com.davfx.util.ClassThreadFactory;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.HttpHeaders;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class HttpService implements AutoCloseable, Closeable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpService.class);
	
	private static final Config CONFIG = ConfigFactory.load(HttpService.class.getClassLoader());
	private static final int THREADS = CONFIG.getInt("ninio.http.service.threads");
	private static final int STREAM_BUFFERING_SIZE = CONFIG.getBytes("ninio.http.service.stream.buffer").intValue();
	
	private final HttpRequestFunctionContainer dispatch;
	private HttpServer server = null;
	private final Queue queue;
	private final ExecutorService executor = Executors.newFixedThreadPool(THREADS, new ClassThreadFactory(HttpService.class));
	
	public HttpService(Queue queue, Address address) {
		this.queue = queue;
		dispatch = new HttpRequestFunctionContainer();
		server = new HttpServer(queue, null, address, new HttpServerHandlerFactory() {
			@Override
			public void failed(IOException e) {
				LOGGER.error("Failed", e);
			}

			@Override
			public void closed() {
				LOGGER.error("Closed");
			}
			
			@Override
			public HttpServerHandler create() {
				return new DispatchHttpServerHandler(dispatch);
			}
		});
	}
	
	@Override
	public void close() {
		executor.shutdown();
		server.close();
	}
	
	public HttpService register(final HttpRequestFilter filter, final HttpServiceHandler handler) {
		final HttpServerHandler h = new HttpServerHandler() {
			@Override
			public void failed(IOException e) {
				LOGGER.trace("Handler failed", e);
			}
			@Override
			public void close() {
				LOGGER.trace("Handler closed");
			}
			
			private HttpController.Http http;
			private Exception error;
			private ByteBufferHandlerInputStream postInOut;
			private Write write = null;
			
			@Override
			public void handle(final HttpRequest request) {
				postInOut = new ByteBufferHandlerInputStream();
				final ByteBufferHandlerInputStream post = postInOut;
				executor.execute(new Runnable() {
					@Override
					public void run() {
						HttpController.Http result;
						Exception err;
						try {
							result = handler.handle(request, new HttpPost() {
								private String string = null;
								private ImmutableMultimap<String, Optional<String>> parameters = null;
								
								@Override
								public InputStream stream() {
									if (string != null) {
										return null;
									}
									return post;
								}
								@Override
								public String toString() {
									if (string != null) {
										return string;
									}
									Charset charset = Charsets.UTF_8;
									for (String h : request.headers.get(HttpHeaderKey.CONTENT_TYPE)) {
										charset = HttpContentType.getContentType(h);
									}
									int l = post.waitFor();
									byte[] b = new byte[l];
									try {
										try (DataInputStream in = new DataInputStream(post)) {
											in.readFully(b, 0, b.length);
										}
									} catch (IOException ioe) {
										LOGGER.error("Could not read post", ioe);
										return null;
									}
									string = new String(b, charset);
									return string;
								}
								@Override
								public ImmutableMultimap<String, Optional<String>> parameters() {
									if (parameters != null) {
										return parameters;
									}
									parameters = HttpPath.of(String.valueOf(HttpSpecification.PATH_SEPARATOR) + HttpSpecification.PARAMETERS_START + toString()).parameters;
									return parameters;
								}
							});
							if (result == null) {
								throw new NullPointerException();
							}
							err = null;
						} catch (Exception e) {
							LOGGER.error("Internal server error", e);
							err = e;
							result = null;
						}
						
						post.close();
						
						final HttpController.Http r = result;
						final Exception e = err;
						queue.post(new Runnable() {
							@Override
							public void run() {
								http = r;
								error = e;
								
								if (write != null) {
									write();
								}
							}
						});
					}
				});
			}
			
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				postInOut.handler.handle(address, buffer);
			}
			
			@Override
			public void ready(Write w) {
				postInOut.handler.close();
				postInOut = null;
				
				write = w;
				if ((http != null) || (error != null)) {
					write();
				}
			}
			
			private void write() {
				if (http == null) {
					write.write(new HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR, HttpMessage.INTERNAL_SERVER_ERROR, ImmutableMultimap.of(HttpHeaders.CONTENT_TYPE, HttpContentType.plainText())));
					write.handle(null, ByteBuffer.wrap(error.getMessage().getBytes(Charsets.UTF_8)));
					write.close();
				} else {
					if (http.async != null) {
						
						final Write w = write;
						http.async.produce(new HttpAsyncOutput() {
							
							private int status = HttpStatus.OK;
							private String reason = HttpMessage.OK;
							private Multimap<String, String> headers = HashMultimap.create();
							private boolean sent = false;

							private void send() {
								if (sent) {
									return;
								}
								if (!headers.containsKey(HttpHeaderKey.CONTENT_TYPE)) {
									headers.put(HttpHeaderKey.CONTENT_TYPE, HttpContentType.plainText());
								}
								w.write(new HttpResponse(status, reason, ImmutableMultimap.copyOf(headers)));
								sent = true;
							}
							
							@Override
							public void close() {
								queue.post(new Runnable() {
									@Override
									public void run() {
										send();
										w.close();
									}
								});
							}
							
							@Override
							public void failed(final IOException e) {
								queue.post(new Runnable() {
									@Override
									public void run() {
										w.failed(e);
									}
								});
							}
							
							@Override
							public HttpAsyncOutput ok() {
								status = HttpStatus.OK;
								reason = HttpMessage.OK;
								return this;
							}
							@Override
							public HttpAsyncOutput notFound() {
								status = HttpStatus.NOT_FOUND;
								reason = HttpMessage.NOT_FOUND;
								return this;
							}
							@Override
							public HttpAsyncOutput internalServerError() {
								status = HttpStatus.INTERNAL_SERVER_ERROR;
								reason = HttpMessage.INTERNAL_SERVER_ERROR;
								return this;
							}

							@Override
							public HttpAsyncOutput header(String key, String value) {
								headers.put(key, value);
								return this;
							}
							@Override
							public HttpAsyncOutput contentType(String contentType) {
								return header(HttpHeaderKey.CONTENT_TYPE, contentType);
							}
							@Override
							public HttpAsyncOutput contentLength(long contentLength) {
								return header(HttpHeaderKey.CONTENT_LENGTH, String.valueOf(contentLength));
							}

							@Override
							public HttpAsyncOutput produce(final ByteBuffer buffer) {
								queue.post(new Runnable() {
									@Override
									public void run() {
										send();
										w.handle(null, buffer);
									}
								});
								return this;
							}

							@Override
							public HttpAsyncOutput produce(String buffer) {
								return produce(ByteBuffer.wrap(buffer.getBytes(Charsets.UTF_8)));
							}
						});
						
					} else {
						
						ImmutableMultimap.Builder<String, String> headers = ImmutableMultimap.builder();
						for (Map.Entry<String, Collection<String>> h : http.headers.asMap().entrySet()) {
							String key = h.getKey();
							if ((key.equals(HttpHeaders.CONTENT_TYPE)) || (key.equals(HttpHeaders.CONTENT_LENGTH)) || (key.equals(HttpHeaders.CONTENT_ENCODING))) {
								String last = null;
								for (String v : h.getValue()) {
									last = v;
								}
								if (last != null) {
									headers.put(key, last);
								}
							} else {
								for (String v : h.getValue()) {
									headers.put(key, v);
								}
							}
						}
						write.write(new HttpResponse(http.status, http.reason, headers.build()));
						
						if (http.stream == null) {
							if (http.content != null) {
								write.handle(null, ByteBuffer.wrap(http.content.getBytes(Charsets.UTF_8)));
							}
							write.close();
						} else {
							final HttpController.HttpStream stream = http.stream;
							final Write w = write;
							executor.execute(new Runnable() {
								@Override
								public void run() {
									try {
										try (OutputStream out = new BufferedOutputStream(new OutputStream() {
											@Override
											public void write(byte[] b, int off, int len) {
												byte[] copy = new byte[len];
												System.arraycopy(b, off, copy, 0, len);
												final ByteBuffer bb = ByteBuffer.wrap(copy);
												queue.post(new Runnable() {
													@Override
													public void run() {
														w.handle(null, bb);
													}
												});
											}
											@Override
											public void write(byte[] b) {
												write(b, 0, b.length);
											}
											@Override
											public void write(int b) {
												byte[] bb = new byte[] { (byte) (b & 0xFF) };
												write(bb);
											}
											@Override
											public void flush() {
											}
											@Override
											public void close() {
												queue.post(new Runnable() {
													@Override
													public void run() {
														w.close();
													}
												});
											}
										}, STREAM_BUFFERING_SIZE)) {
											stream.produce(out);
										}
									} catch (Exception e) {
										LOGGER.error("Internal server error", e);
										final IOException ioe = new IOException(e);
										queue.post(new Runnable() {
											@Override
											public void run() {
												w.failed(ioe);
											}
										});
									}
									
									queue.post(new Runnable() {
										@Override
										public void run() {
											w.close();
										}
									});
								}
							});
						}
						
					}
				}
				
				write = null;
				http = null;
				error = null;
			}
		};
		
		queue.post(new Runnable() {
			@Override
			public void run() {
				dispatch.add(filter, h);
			}
		});
		
		return this;
	}

}
