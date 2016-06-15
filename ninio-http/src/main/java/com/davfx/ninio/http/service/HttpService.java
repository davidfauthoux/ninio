package com.davfx.ninio.http.service;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.http.HttpContentReceiver;
import com.davfx.ninio.http.HttpContentSender;
import com.davfx.ninio.http.HttpHeaderKey;
import com.davfx.ninio.http.HttpListening;
import com.davfx.ninio.http.HttpListeningHandler;
import com.davfx.ninio.http.HttpMessage;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpSpecification;
import com.davfx.ninio.http.HttpStatus;
import com.davfx.ninio.http.service.HttpController.HttpAsyncOutput;
import com.davfx.ninio.util.ClassThreadFactory;
import com.davfx.ninio.util.ConfigUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.HttpHeaders;
import com.typesafe.config.Config;

public final class HttpService implements HttpListeningHandler, Disconnectable, AutoCloseable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpService.class);
	
	private static final Config CONFIG = ConfigUtils.load(HttpListening.class);
	private static final int DEFAULT_THREADS = CONFIG.getInt("service.threads");
	private static final int STREAMING_BUFFER_SIZE = CONFIG.getBytes("service.stream.buffer").intValue();
	private static final double STREAMING_EPSILON = ConfigUtils.getDuration(CONFIG, "service.stream.epsilon");
	
	public static interface Builder {
		interface HandlerBuilder {
			HandlerBuilder limit(double streamingRate);
		}

		Builder threading(int threads);
		HandlerBuilder register(HttpServiceHandler handler);
		HttpService build();
	}

	private static final class HandlerElement {
		public HttpServiceHandler handler;
		public double streamingRate = Double.NaN;
	}

	public static Builder builder() {
		return new Builder() {
			private final ImmutableList.Builder<HandlerElement> handlers = ImmutableList.builder();
			private int threads = DEFAULT_THREADS;
			
			@Override
			public Builder threading(int threads) {
				this.threads = threads;
				return this;
			}
			
			@Override
			public HandlerBuilder register(HttpServiceHandler handler) {
				final HandlerElement h = new HandlerElement();
				h.handler = handler;
				handlers.add(h);
				return new HandlerBuilder() {
					@Override
					public HandlerBuilder limit(double streamingRate) {
						h.streamingRate = streamingRate;
						return this;
					}
				};
			}
			
			@Override
			public HttpService build() {
				return new HttpService(threads, handlers.build());
			}
		};
	}
	
	private final ExecutorService executor;
	private final ImmutableList<HandlerElement> handlers;
	
	private HttpService(int threads, ImmutableList<HandlerElement> handlers) {
		this.handlers = handlers;
		executor = Executors.newFixedThreadPool(threads, new ClassThreadFactory(HttpService.class));
	}
	
	@Override
	public void close() {
		executor.shutdown();
	}
	
	@Override
	public HttpListeningHandler.ConnectionHandler create() {
		return new HttpListeningHandler.ConnectionHandler() {
			@Override
			public void closed() {
			}
			
			@Override
			public HttpContentReceiver handle(final HttpRequest request, final ResponseHandler responseHandler) {
				return new HttpContentReceiver() {
					
					private final Object lock = new Object();
					private final ByteBufferHandlerInputStream post = new ByteBufferHandlerInputStream();
					private HttpController.Http http = null;
					private double streamingRate = Double.NaN;
					
					{
						executor.execute(new Runnable() {
							@Override
							public void run() {
								HttpController.Http r = null;
								
								for (HandlerElement h : handlers) {
									try {
										r = h.handler.handle(new HttpServiceRequest(request), new HttpPost() {
											private String string = null;
											private ImmutableMultimap<String, Optional<String>> parameters = null;
											
											@Override
											public synchronized InputStream stream() {
												if (string != null) {
													return null;
												}
												return post;
											}
											@Override
											public synchronized String toString() {
												if (string != null) {
													return string;
												}
												Charset charset = null;
												for (String h : request.headers.get(HttpHeaderKey.CONTENT_TYPE)) {
													charset = HttpContentType.getContentType(h);
												}
												if (charset == null) {
													charset = Charsets.UTF_8;
												}
												int l = post.waitFor();
												LOGGER.trace("Post size: {} bytes", l);
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
											public synchronized ImmutableMultimap<String, Optional<String>> parameters() {
												if (parameters != null) {
													return parameters;
												}
												parameters = HttpRequest.parameters(HttpSpecification.PARAMETERS_START + toString());
												return parameters;
											}
										});
										
										if (r != null) {
											streamingRate = h.streamingRate;
											break;
										}
									} catch (Exception e) {
										r = null;
										HttpContentSender sender = responseHandler.send(new HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR, HttpMessage.INTERNAL_SERVER_ERROR, ImmutableMultimap.of(HttpHeaders.CONTENT_TYPE, HttpContentType.plainText())));
										sender.send(ByteBuffer.wrap(e.getMessage().getBytes(Charsets.UTF_8)));
										sender.finish();
										break;
									}
								}

								if (r == null) {
									HttpContentSender sender = responseHandler.send(new HttpResponse(HttpStatus.NOT_FOUND, HttpMessage.NOT_FOUND));
									sender.finish();
								}

								synchronized (lock) {
									http = r;
									lock.notifyAll();
								}
							}
						});
					}
					
					@Override
					public void received(ByteBuffer buffer) {
						post.receiver.received(buffer);
					}
					
					@Override
					public void ended() {
						executor.execute(new Runnable() {
							@Override
							public void run() {
								synchronized (lock) {
									while (http == null) {
										try {
											lock.wait();
										} catch (InterruptedException ie) {
										}
									}
								}
								
								if (http.async != null) {
									http.async.produce(new HttpAsyncOutput() {
										
										private int status = HttpStatus.OK;
										private String reason = HttpMessage.OK;
										private Multimap<String, String> headers = HashMultimap.create();
										private HttpContentSender sender = null;
	
										private synchronized void send() {
											if (sender != null) {
												return;
											}
											if (!headers.containsKey(HttpHeaderKey.CONTENT_TYPE)) {
												headers.put(HttpHeaderKey.CONTENT_TYPE, HttpContentType.plainText());
											}
											sender = responseHandler.send(new HttpResponse(status, reason, ImmutableMultimap.copyOf(headers)));
										}
										
										@Override
										public void finish() {
											executor.execute(new Runnable() {
												@Override
												public void run() {
													send();
													sender.finish();
												}
											});
										}
										
										@Override
										public void cancel() {
											executor.execute(new Runnable() {
												@Override
												public void run() {
													send();
													sender.cancel();
												}
											});
										}
										
										@Override
										public synchronized HttpAsyncOutput ok() {
											status = HttpStatus.OK;
											reason = HttpMessage.OK;
											return this;
										}
										@Override
										public synchronized HttpAsyncOutput notFound() {
											status = HttpStatus.NOT_FOUND;
											reason = HttpMessage.NOT_FOUND;
											return this;
										}
										@Override
										public synchronized HttpAsyncOutput internalServerError() {
											status = HttpStatus.INTERNAL_SERVER_ERROR;
											reason = HttpMessage.INTERNAL_SERVER_ERROR;
											return this;
										}
										@Override
										public synchronized HttpAsyncOutput badRequest() {
											status = HttpStatus.BAD_REQUEST;
											reason = HttpMessage.BAD_REQUEST;
											return this;
										}
	
										@Override
										public synchronized HttpAsyncOutput header(String key, String value) {
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
											executor.execute(new Runnable() {
												@Override
												public void run() {
													send();
													sender.send(buffer);
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

									final HttpContentSender sender = responseHandler.send(new HttpResponse(http.status, http.reason, headers.build()));

									if (http.stream == null) {
										if (http.content != null) {
											//TODO For big content, use streamingRate
											sender.send(ByteBuffer.wrap(http.content.getBytes(Charsets.UTF_8)));
										}
										sender.finish();
									} else {
										try {
											try {
												double timeToWait = 0d;
												double timeWaited = 0d;
												
												while (true) {
													byte[] b = new byte[STREAMING_BUFFER_SIZE];
													int l = http.stream.read(b);
													if (l <= 0) {
														break;
													}
													
													if (!Double.isNaN(streamingRate)) {
														double t = l / streamingRate;
														timeToWait += t;
														
														double delta = timeToWait - timeWaited;
														if (delta >= STREAMING_EPSILON) {
															long deltaMs = (long) Math.floor(delta * 1000d);
															Thread.sleep(deltaMs);
															timeWaited += deltaMs / 1000d;
														}
													}
													
													sender.send(ByteBuffer.wrap(b, 0, l));
												}
												
												sender.finish();
											} finally {
												http.stream.close();
											}
										} catch (Exception e) {
											LOGGER.error("Internal server error", e);
											sender.cancel();
										}
									}
								}
							}
						});
					}
				};
			}
		};
	}
}
