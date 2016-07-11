package com.davfx.ninio.http.service;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.SerialExecutor;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.net.HttpHeaders;
import com.typesafe.config.Config;

public final class HttpService implements HttpListeningHandler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpService.class);
	
	private static final Config CONFIG = ConfigUtils.load(HttpListening.class);
	private static final int DEFAULT_THREADS = CONFIG.getInt("service.threads");
	private static final int STREAMING_BUFFER_SIZE = CONFIG.getBytes("service.stream.buffer").intValue();
	
	public static interface Builder {
		/*%%
		interface HandlerBuilder {
			HandlerBuilder limit(double streamingRate);
		}
		*/

		Builder threading(int threads);
		Builder register(HttpServiceHandler handler);
		HttpService build();
	}

	/*%%
	private static final class HandlerElement {
		public HttpServiceHandler handler;
		public double streamingRate = Double.NaN;
	}
	*/

	public static Builder builder() {
		return new Builder() {
			private final ImmutableList.Builder<HttpServiceHandler> handlers = ImmutableList.builder();
			private int threads = DEFAULT_THREADS;
			
			@Override
			public Builder threading(int threads) {
				this.threads = threads;
				return this;
			}
			
			@Override
			public Builder register(HttpServiceHandler handler) {
				//%% final HandlerElement h = new HandlerElement();
				//%% h.handler = handler;
				handlers.add(handler);
				/*%%
				return new HandlerBuilder() {
					@Override
					public HandlerBuilder limit(double streamingRate) {
						h.streamingRate = streamingRate;
						return this;
					}
				};
				*/
				return this;
			}
			
			@Override
			public HttpService build() {
				return new HttpService(threads, handlers.build());
			}
		};
	}
	
	private final Executor[] executors;
	private final AtomicInteger loop = new AtomicInteger(0);
	private final ImmutableList<HttpServiceHandler> handlers;
	
	private HttpService(int threads, ImmutableList<HttpServiceHandler> handlers) {
		this.handlers = handlers;
		executors = new Executor[threads];
		for (int i = 0; i < executors.length; i++) {
			executors[i] = new SerialExecutor(HttpService.class);
		}
	}
	
	private void execute(Runnable r) {
		executors[Math.abs(loop.getAndIncrement()) % executors.length].execute(r);
	}
	
	private static final class StreamTask {
		public final InputStream stream;
		public final HttpContentSender sender;
		
		public StreamTask(InputStream stream, HttpContentSender sender) {
			this.stream = stream;
			this.sender = sender;
		}

		public boolean run() {
			byte[] b = new byte[STREAMING_BUFFER_SIZE];
			int l;
			try {
				l = stream.read(b);
			} catch (IOException ee) {
				LOGGER.error("Could not read stream", ee);
				try {
					stream.close();
				} catch (IOException ce) {
				}
				sender.cancel();
				return false;
			}
			if (l <= 0) {
				try {
					stream.close();
				} catch (IOException ce) {
				}
				sender.finish();
				return false;
			} else {
				sender.send(ByteBuffer.wrap(b, 0, l));
				return true;
			}

		}
	}
	
	@Override
	public HttpListeningHandler.ConnectionHandler create() {
		return new HttpListeningHandler.ConnectionHandler() {
			private final Object lock = new Object();
			private boolean closed = false;
			private final Set<StreamTask> registeredTasks = new HashSet<>();
			private long currentBuffering = 0L;
			
			@Override
			public void closed() {
				synchronized (lock) {
					for (StreamTask t : registeredTasks) {
						try {
							t.stream.close();
						} catch (IOException ce) {
						}
					}
					registeredTasks.clear();
				}
			}
			
			@Override
			public void buffering(long size) {
				synchronized (lock) {
					currentBuffering = size;
					
					if (currentBuffering == 0L) {
						Iterator<StreamTask> i = registeredTasks.iterator();
						while (i.hasNext()) {
							StreamTask t = i.next();
							if (!t.run()) {
								i.remove();
								continue;
							}
							break;
							// Only one task is executed, it'll write to the stream, and so this buffering() method will be called again
						}
					}
				}
			}
			
			@Override
			public HttpContentReceiver handle(final HttpRequest request, final ResponseHandler responseHandler) {
				return new HttpContentReceiver() {
					
					private final ByteBufferHandlerInputStream post = new ByteBufferHandlerInputStream();
					private HttpController.Http http = null;
					//%% private double streamingRate = Double.NaN;
					
					{
						execute(new Runnable() {
							@Override
							public void run() {
								HttpController.Http r = null;
								
								for (HttpServiceHandler h : handlers) {
									try {
										r = h.handle(new HttpServiceRequest(request), new HttpPost() {
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
											//%% streamingRate = h.streamingRate;
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
									if (closed) {
										if (r.stream != null) {
											try {
												r.stream.close();
											} catch (IOException ce) {
											}
										}
										return;
									}
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
						post.receiver.ended();
						execute(new Runnable() {
							@Override
							public void run() {
								synchronized (lock) {
									while (http == null) {
										if (closed) {
											return;
										}
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
	
										private void send() {
											if (sender != null) {
												return;
											}
											if (!headers.containsKey(HttpHeaderKey.CONTENT_TYPE)) {
												headers.put(HttpHeaderKey.CONTENT_TYPE, HttpContentType.plainText());
											}
											sender = responseHandler.send(new HttpResponse(status, reason, ImmutableMultimap.copyOf(headers)));
										}
										
										@Override
										public synchronized void finish() {
											send();
											sender.finish();
										}
										
										@Override
										public synchronized void cancel() {
											send();
											sender.cancel();
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
										public synchronized HttpAsyncOutput produce(final ByteBuffer buffer) {
											send();
											sender.send(buffer);
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
											//TODO For big content, use new InputStream(http.content)
											sender.send(ByteBuffer.wrap(http.content.getBytes(Charsets.UTF_8)));
										}
										sender.finish();
									} else {
										synchronized (lock) {
											StreamTask task = new StreamTask(http.stream, sender);
											
											if (currentBuffering == 0L) {
												if (task.run()) {
													registeredTasks.add(task);
												}
											} else {
												registeredTasks.add(task);
											}
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
