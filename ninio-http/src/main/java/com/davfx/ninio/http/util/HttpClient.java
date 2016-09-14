package com.davfx.ninio.http.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferAllocator;
import com.davfx.ninio.core.Limit;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.TcpSocket;
import com.davfx.ninio.core.Timeout;
import com.davfx.ninio.dns.DnsConnecter;
import com.davfx.ninio.dns.DnsConnection;
import com.davfx.ninio.http.HttpConnecter;
import com.davfx.ninio.http.HttpContentSender;
import com.davfx.ninio.http.HttpHeaderKey;
import com.davfx.ninio.http.HttpLimit;
import com.davfx.ninio.http.HttpMethod;
import com.davfx.ninio.http.HttpReceiver;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpRequestAddress;
import com.davfx.ninio.http.HttpRequestBuilder;
import com.davfx.ninio.http.HttpSpecification;
import com.davfx.ninio.http.HttpTimeout;
import com.davfx.ninio.http.UrlUtils;
import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.SerialExecutor;
import com.google.common.collect.ImmutableMultimap;
import com.typesafe.config.Config;

public final class HttpClient implements AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpClient.class);
	
	private static final Config CONFIG = ConfigUtils.load(new com.davfx.ninio.http.dependencies.Dependencies(), com.davfx.ninio.http.HttpClient.class);
	private static final double DEFAULT_TIMEOUT = ConfigUtils.getDuration(CONFIG, "default.timeout");
	private static final int DEFAULT_LIMIT = CONFIG.getInt("default.limit");

	private final Ninio ninio = Ninio.create();
	private final SerialExecutor executor = new SerialExecutor(HttpClient.class);
	private final HttpConnecter httpClient;
	private final DnsConnecter dnsClient;
	private final Timeout timeoutManager = new Timeout();
	private final Limit limitManager = new Limit();

	public HttpClient() {
		dnsClient = ninio.create(com.davfx.ninio.dns.DnsClient.builder().with(executor));
		dnsClient.connect(new DnsConnection() {
			@Override
			public void closed() {
			}

			@Override
			public void failed(IOException e) {
				LOGGER.error("Unable to create DNS client", e);
			}

			@Override
			public void connected(Address address) {
			}
		});

		httpClient = ninio.create(com.davfx.ninio.http.HttpClient.builder().with(executor).with(dnsClient).with(TcpSocket.builder().with(new ByteBufferAllocator() {
			@Override
			public ByteBuffer allocate() {
				return ByteBuffer.allocate(1024);
			}
		})));
	}
	
	/*%%
	private static final class ToSend {
		public final ByteBuffer buffer;
		public final SendCallback callback;
		public ToSend(ByteBuffer buffer, SendCallback callback) {
			this.buffer = buffer;
			this.callback = callback;
		}
	}

	private static final class InternalHttpContentSender implements HttpContentSender {
		private final SerialExecutor executor;
		private final List<ToSend> toSend = new LinkedList<>();
		private boolean finished = false;
		private boolean canceled = false;
		private HttpContentSender contentSender = null;
		
		public InternalHttpContentSender(SerialExecutor executor) {
			this.executor = executor;
		}
		
		@Override
		public HttpContentSender send(final ByteBuffer buffer, final SendCallback callback) {
			executor.execute(new Runnable() {
				@Override
				public void run() {
					if (canceled) {
						callback.failed(new IOException("Canceled"));
						return;
					}
					if (finished) {
						throw new IllegalStateException("Finished");
					}
					
					if (contentSender != null) {
			            contentSender.send(buffer, callback);
					} else {
						toSend.add(new ToSend(buffer, callback));
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
					finished = true;
					
					if (contentSender != null) {
			            contentSender.finish();
					}
				}
			});
		}
		
		@Override
		public void cancel() {
			executor.execute(new Runnable() {
				@Override
				public void run() {
					canceled = true;
					toSend.clear();
					
					if (contentSender != null) {
			            contentSender.cancel();
					}
				}
			});
		}
		
		private void set(final HttpContentSender s) {
			executor.execute(new Runnable() {
				@Override
				public void run() {
					contentSender = s;
					
					if (canceled) {
						contentSender.cancel();
					} else {
						for (ToSend s : toSend) {
				            contentSender.send(s.buffer, s.callback);
				        }
						toSend.clear();
						
						if (finished) {
							contentSender.finish();
						}
					}
				}
			});
		}
	}
	*/

	public final class Request {
		private String host = null;
		private int port = -1;
		private boolean secure = false;
		private String path = String.valueOf(HttpSpecification.PATH_SEPARATOR);
		private final ImmutableMultimap.Builder<String, String> headers = ImmutableMultimap.builder();
		private HttpReceiver receiver = null;
		private double timeout = DEFAULT_TIMEOUT;
		private int limit = DEFAULT_LIMIT;
		
		public Request url(String url) {
			UrlUtils.ParsedUrl parsedUrl = UrlUtils.parse(url);
			host = parsedUrl.host;
			port = parsedUrl.port;
			secure = parsedUrl.secure;
			path = parsedUrl.path;
			for (Map.Entry<String, Collection<String>> e : parsedUrl.headers.asMap().entrySet()) {
				headers.putAll(e.getKey(), e.getValue());
			}
			return this;
		}
		
		public Request timeout(double timeout) {
			this.timeout = timeout;
			return this;
		}
		public Request limit(int limit) {
			this.limit = limit;
			return this;
		}
		public Request host(String host) {
			this.host = host;
			return this;
		}
		public Request port(int port) {
			this.port = port;
			return this;
		}
		public Request secure(boolean secure) {
			this.secure = secure;
			return this;
		}
		public Request path(String path) {
			this.path = path;
			return this;
		}
		public Request header(String key, String value) {
			headers.put(key, value);
			return this;
		}
		public Request receive(HttpReceiver receiver) {
			this.receiver = receiver;
			return this;
		}
		
		public void get() {
			if (host == null) {
				throw new NullPointerException("host");
			}
			if (receiver == null) {
				throw new NullPointerException("receiver");
			}
			if (port < 0) {
				port = secure ? HttpSpecification.DEFAULT_SECURE_PORT : HttpSpecification.DEFAULT_PORT;
			}

	        HttpRequestBuilder requestReceiverBuilder = httpClient.request();

	        HttpRequest httpRequest = new HttpRequest(new HttpRequestAddress(host, port, secure), HttpMethod.GET, path, UrlUtils.merge(headers.build(), ImmutableMultimap.of(HttpHeaderKey.HOST, host)));
	        
	        HttpTimeout.wrap(timeoutManager, timeout, HttpLimit.wrap(limitManager, limit, requestReceiverBuilder))
				.build(httpRequest)
				.receive(receiver)
				.finish();;
		}

		public HttpContentSender post() {
			if (host == null) {
				throw new NullPointerException("host");
			}
			if (receiver == null) {
				throw new NullPointerException("receiver");
			}
			if (port < 0) {
				port = secure ? HttpSpecification.DEFAULT_SECURE_PORT : HttpSpecification.DEFAULT_PORT;
			}
			
	        HttpRequestBuilder requestReceiverBuilder = httpClient.request();

	        HttpRequest httpRequest = new HttpRequest(new HttpRequestAddress(host, port, secure), HttpMethod.POST, path, UrlUtils.merge(headers.build(), ImmutableMultimap.of(HttpHeaderKey.HOST, host)));
	        
	        return HttpTimeout.wrap(timeoutManager, timeout, HttpLimit.wrap(limitManager, limit, requestReceiverBuilder))
				.build(httpRequest)
				.receive(receiver);
		}
	}
	
	public Request request() {
		return new Request();
	}

	public void close() {
		httpClient.close();
		dnsClient.close();
		timeoutManager.close();
		ninio.close();
	}

}
