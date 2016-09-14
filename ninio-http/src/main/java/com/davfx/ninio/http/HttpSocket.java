package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferAllocator;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.Nop;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.core.TcpSocket;
import com.google.common.collect.ImmutableMultimap;

public final class HttpSocket implements Connecter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpSocket.class);

	public static interface Builder extends TcpSocket.Builder {
		Builder to(Address connectAddress);
		Builder with(HttpConnecter httpClient);
		Builder route(String path);
	}

	public static Builder builder() {
		return new Builder() {
			private HttpConnecter httpClient = null;
			private String path = String.valueOf(HttpSpecification.PATH_SEPARATOR);
			
			private Address connectAddress = null;
			
			@Override
			public TcpSocket.Builder with(ByteBufferAllocator byteBufferAllocator) {
				return this;
			}
			@Override
			public TcpSocket.Builder bind(Address bindAddress) {
				return this;
			}
			
			@Override
			public Builder to(Address connectAddress) {
				this.connectAddress = connectAddress;
				return this;
			}
			
			@Override
			public Builder with(HttpConnecter httpClient) {
				this.httpClient = httpClient;
				return this;
			}
			
			@Override
			public Builder route(String path) {
				this.path = path;
				return this;
			}
			
			@Override
			public Connecter create(Queue queue) {
				if (httpClient == null) {
					throw new NullPointerException("httpClient");
				}
				return new HttpSocket(queue, httpClient, path, connectAddress);
			}
		};
	}

	private final Queue queue;
	private final HttpContentSender sender;
	private Connection connection = null;
	private boolean closed = false;
	
	private HttpSocket(final Queue queue, HttpConnecter httpClient, String path, final Address connectAddress) {
		this.queue = queue;
		
		HttpRequest request = new HttpRequest(new HttpRequestAddress(Address.ipToString(connectAddress.ip), connectAddress.port, false), HttpMethod.POST, path, ImmutableMultimap.<String, String>builder()
			// GZIP deflate cannot stream/flush
			.put(HttpHeaderKey.CONTENT_ENCODING, HttpHeaderValue.IDENTITY)
			.put(HttpHeaderKey.ACCEPT_ENCODING, HttpHeaderValue.IDENTITY)
			.build()
		);

		HttpRequestBuilder b = httpClient.request();
		sender = b.build(request);
		b.receive(new HttpReceiver() {
			@Override
			public HttpContentReceiver received(final HttpResponse response) {
				if (response.status != 200) {
					queue.execute(new Runnable() {
						@Override
						public void run() {
							if (closed) {
								return;
							}
							if (connection != null) {
								closed = true;
								connection.failed(new IOException("[" + response.status + " " + response.reason + "]"));
							}
						}
					});
					return null;
				}
				
				return new HttpContentReceiver() {
					@Override
					public void received(final ByteBuffer buffer) {
						queue.execute(new Runnable() {
							@Override
							public void run() {
								if (closed) {
									return;
								}
								if (connection != null) {
									connection.received(null, buffer);
								}
							}
						});
					}
					
					@Override
					public void ended() {
						LOGGER.debug("Connection abruptly closed by peer");
						sender.cancel();
						queue.execute(new Runnable() {
							@Override
							public void run() {
								if (closed) {
									return;
								}
								if (connection != null) {
									closed = true;
									connection.closed();
								}
							}
						});
					}
				};
			}
			
			@Override
			public void failed(final IOException ioe) {
				queue.execute(new Runnable() {
					@Override
					public void run() {
						if (closed) {
							return;
						}
						if (connection != null) {
							closed = true;
							connection.failed(ioe);
						}
					}
				});
			}
		});
	}

	@Override
	public void close() {
		sender.cancel();
	}
	
	@Override
	public void send(Address address, ByteBuffer buffer, final SendCallback callback) {
		sender.send(buffer, new SendCallback() {
			@Override
			public void failed(final IOException e) {
				queue.execute(new Runnable() {
					@Override
					public void run() {
						callback.failed(e);
					}
				});
			}
			
			@Override
			public void sent() {
				queue.execute(new Runnable() {
					@Override
					public void run() {
						if (closed) {
							return;
						}
						callback.sent();
					}
				});
			}
		});
	}
	
	@Override
	public void connect(final Connection callback) {
		queue.execute(new Runnable() {
			@Override
			public void run() {
				if (closed) {
					callback.failed(new IOException("Closed"));
					return;
				}
				connection = callback;
				callback.connected(null);
			}
		});
		sender.send(ByteBuffer.allocate(0), new Nop());
	}
}
