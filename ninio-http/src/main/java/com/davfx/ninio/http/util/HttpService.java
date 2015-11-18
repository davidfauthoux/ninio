package com.davfx.ninio.http.util;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.http.DispatchHttpServerHandler;
import com.davfx.ninio.http.HttpContentType;
import com.davfx.ninio.http.HttpHeaderExtension;
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
import com.davfx.util.ClassThreadFactory;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.net.HttpHeaders;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class HttpService implements AutoCloseable, Closeable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpService.class);
	
	private static final Config CONFIG = ConfigFactory.load(HttpService.class.getClassLoader());
	private static final int THREADS = CONFIG.getInt("ninio.http.service.threads");

	private final HttpRequestFunctionContainer dispatch;
	private HttpServer server = null;
	private final Queue queue = new Queue();
	private final ExecutorService executor = Executors.newFixedThreadPool(THREADS, new ClassThreadFactory(HttpService.class));
	
	public HttpService() {
		dispatch = new HttpRequestFunctionContainer();
	}
	
	@Override
	public void close() {
		executor.shutdown();
		if (server != null) {
			server.close();
		}
	}
	
	public HttpService start(int port) {
		server = new HttpServer(queue, null, new Address(Address.ANY, port), new HttpServerHandlerFactory() {
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
		return this;
	}
	
	public HttpService register(HttpRequestFilter filter, final HttpServiceHandler handler) {
		HttpServerHandler h = new HttpServerHandler() {
			@Override
			public void failed(IOException e) {
			}
			@Override
			public void close() {
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
										String c = HttpHeaderExtension.extract(h, HttpHeaderKey.CHARSET);
										if (c != null) {
											charset = Charset.forName(c);
											break;
										}
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
									parameters = new HttpPath(String.valueOf(HttpSpecification.PATH_SEPARATOR) + HttpSpecification.PARAMETERS_START + toString()).parameters;
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
					write.write(new HttpResponse(http.status, http.reason, ImmutableMultimap.of(HttpHeaders.CONTENT_TYPE, http.contentType)));
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
									stream.produce(new HttpController.HttpStream.OutputStreamFactory() {
										@Override
										public OutputStream open() throws IOException {
											return new OutputStream() {
												@Override
												public void write(byte[] b, int off, int len) throws IOException {
													w.handle(null, ByteBuffer.wrap(b, off, len));
												}
												@Override
												public void write(byte[] b) throws IOException {
													write(b, 0, b.length);
												}
												@Override
												public void write(int b) throws IOException {
													byte[] bb = new byte[] { (byte) (b & 0xFF) };
													write(bb);
												}
												@Override
												public void flush() throws IOException {
												}
												@Override
												public void close() throws IOException {
													w.close();
												}
											};
										}
									});
								} catch (Exception e) {
									LOGGER.error("Internal server error", e);
									w.write(new HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR, HttpMessage.INTERNAL_SERVER_ERROR, ImmutableMultimap.of(HttpHeaders.CONTENT_TYPE, HttpContentType.plainText())));
									w.handle(null, ByteBuffer.wrap(e.getMessage().getBytes(Charsets.UTF_8)));
									w.close();
								}
							}
						});
					}
				}
				
				write = null;
				http = null;
				error = null;
			}
		};
		
		dispatch.add(filter, h);
		
		return this;
	}

}
