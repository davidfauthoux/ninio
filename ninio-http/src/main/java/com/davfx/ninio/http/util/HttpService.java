package com.davfx.ninio.http.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.http.DispatchHttpServerHandler;
import com.davfx.ninio.http.HttpContentType;
import com.davfx.ninio.http.HttpMessage;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpRequestFunctionContainer;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpServer;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;
import com.davfx.ninio.http.HttpStatus;
import com.davfx.ninio.util.GlobalQueue;
import com.davfx.util.ClassThreadFactory;
import com.google.common.base.Charsets;
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
		server = new HttpServer(GlobalQueue.get(), null, new Address(Address.ANY, port), new HttpServerHandlerFactory() {
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
			private HttpRequest request;
			private File postFile = null;
			private OutputStream postOutputStream = null;
			private boolean postError = false;

			@Override
			public void failed(IOException e) {
			}
			@Override
			public void close() {
			}
			
			@Override
			public void handle(HttpRequest request) {
				this.request = request;
			}
			
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				if (!postError && (postOutputStream == null)) {
					try {
						postFile = Files.createTempFile("post", ".data").toFile();
					} catch (IOException ioe) {
						LOGGER.error("Could not open post file: {}", postFile.getAbsolutePath(), ioe);
						postFile = null;
						postError = true;
					}
					if (postFile != null) {
						postFile.deleteOnExit();
						try {
							postOutputStream = new FileOutputStream(postFile);
						} catch (IOException ioe) {
							LOGGER.error("Could not write post to: {}", postFile.getAbsolutePath(), ioe);
							postFile = null;
							postError = true;
						}
					}
				}
				
				if (postOutputStream != null) {
					try {
						postOutputStream.write(buffer.array(), buffer.position(), buffer.remaining());
					} catch (IOException ioe) {
						LOGGER.error("Could not write post to: {}", postFile.getAbsolutePath(), ioe);
						try {
							postOutputStream.close();
						} catch (IOException e) {
						}
						postOutputStream = null;
						postFile = null;
						postError = true;
					}
				}
			}
			
			@Override
			public void ready(final Write write) {
				if (postOutputStream != null) {
					try {
						postOutputStream.close();
					} catch (IOException ioe) {
						LOGGER.error("Could not close post file: {}", postFile.getAbsolutePath(), ioe);
						postFile = null;
					}
					postOutputStream = null;
				}
				
				InputStream postInputStream;
				if (postFile != null) {
					try {
						postInputStream = new FileInputStream(postFile);
					} catch (IOException ioe) {
						LOGGER.error("Could not open post file: {}", postFile.getAbsolutePath(), ioe);
						postInputStream = null;
					}
				} else {
					postInputStream = null;
				}
				
				final InputStream post = postInputStream;
				final HttpRequest r = request;
				executor.execute(new Runnable() {
					@Override
					public void run() {
						try {
							handler.handle(r, post, new HttpServiceResult() {
								private String contentType = HttpContentType.plainText();
								@Override
								public HttpServiceResult contentType(String contentType) {
									this.contentType = contentType;
									return this;
								}
								@Override
								public void success(String content) {
									write.write(new HttpResponse(HttpStatus.OK, HttpMessage.OK, ImmutableMultimap.of(HttpHeaders.CONTENT_TYPE, HttpContentType.plainText())));
									write.handle(null, ByteBuffer.wrap(content.getBytes(Charsets.UTF_8)));
								}
								@Override
								public OutputStream success() {
									write.write(new HttpResponse(HttpStatus.OK, HttpMessage.OK, ImmutableMultimap.of(HttpHeaders.CONTENT_TYPE, contentType)));
									return new OutputStream() {
										@Override
										public void write(byte[] b, int off, int len) throws IOException {
											write.handle(null, ByteBuffer.wrap(b, off, len));
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
										}
									};
								}
							});
						} catch (IOException ioe) {
							write.write(new HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR, HttpMessage.INTERNAL_SERVER_ERROR, ImmutableMultimap.of(HttpHeaders.CONTENT_TYPE, HttpContentType.plainText())));
							write.handle(null, ByteBuffer.wrap(ioe.getMessage().getBytes(Charsets.UTF_8)));
						}
						write.close();
					}
				});
				
				if (postFile != null) {
					postFile.delete();
					postFile = null;
				}
				postError = false;
				request = null;
			}
		};
		
		dispatch.add(filter, h);
		
		return this;
	}

}
