package com.davfx.ninio.http.v3;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.ByteBufferAllocator;
import com.davfx.ninio.core.v3.Closing;
import com.davfx.ninio.core.v3.Connecting;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.Queue;
import com.davfx.ninio.core.v3.Receiver;
import com.davfx.ninio.core.v3.TcpSocket;
import com.google.common.collect.ImmutableMultimap;

public final class HttpSocket implements Connector {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpSocket.class);

	public static interface Builder extends TcpSocket.Builder {
		Builder to(Address connectAddress);
		Builder with(HttpClient httpClient);
		Builder route(String path);
		Builder failing(Failing failing);
		Builder closing(Closing closing);
		Builder connecting(Connecting connecting);
		Builder receiving(Receiver receiver);
	}

	public static Builder builder() {
		return new Builder() {
			private HttpClient httpClient = null;
			private String path = String.valueOf(HttpSpecification.PATH_SEPARATOR);
			
			private Address connectAddress = null;
			
			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			
			@Override
			public TcpSocket.Builder with(ByteBufferAllocator byteBufferAllocator) {
				return this;
			}
			@Override
			public TcpSocket.Builder bind(Address bindAddress) {
				return this;
			}
			
			@Override
			public Builder closing(Closing closing) {
				this.closing = closing;
				return this;
			}
		
			@Override
			public Builder connecting(Connecting connecting) {
				this.connecting = connecting;
				return this;
			}
			
			@Override
			public Builder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public Builder receiving(Receiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
			@Override
			public Builder to(Address connectAddress) {
				this.connectAddress = connectAddress;
				return this;
			}
			
			@Override
			public Builder with(HttpClient httpClient) {
				this.httpClient = httpClient;
				return this;
			}
			
			@Override
			public Builder route(String path) {
				this.path = path;
				return this;
			}
			
			@Override
			public Connector create(Queue queue) {
				if (httpClient == null) {
					throw new NullPointerException("httpClient");
				}
				return new HttpSocket(httpClient, path, connectAddress, connecting, closing, failing, receiver);
			}
		};
	}

	private final HttpContentSender sender;
	
	private HttpSocket(HttpClient httpClient, String path, final Address connectAddress, final Connecting connecting, final Closing closing, final Failing failing, final Receiver receiver) {
		HttpRequest request = new HttpRequest(connectAddress, false, HttpMethod.GET, path, ImmutableMultimap.<String, String>builder()
			// GZIP deflate cannot stream/flush
			.put(HttpHeaderKey.CONTENT_ENCODING, HttpHeaderValue.IDENTITY)
			.put(HttpHeaderKey.ACCEPT_ENCODING, HttpHeaderValue.IDENTITY)
			.build()
		);

		if (connecting != null) {
			connecting.connected(connectAddress, this);
		}
		
		sender = httpClient.request()
			.failing(failing)
			.receiving(new HttpReceiver() {
				@Override
				public HttpContentReceiver received(Disconnectable disconnectable, HttpResponse response) {
					if (response.status != 200) {
						if (failing != null) {
							failing.failed(new IOException("Could not connect to " + connectAddress + " [" + response.status + " " + response.reason + "]"));
						}
						return null;
					}
					
					return new HttpContentReceiver() {
						@Override
						public void received(ByteBuffer buffer) {
							if (receiver != null) {
								receiver.received(HttpSocket.this, null, buffer);
							}
						}
						
						@Override
						public void ended() {
							LOGGER.debug("Connection abruptly closed by peer");
							sender.cancel();
							if (closing != null) {
								closing.closed();
							}
						}
					};
				}
			})
			.build(request);
	}

	@Override
	public void close() {
		sender.cancel();
	}
	
	@Override
	public Connector send(Address address, ByteBuffer buffer) {
		sender.send(buffer);
		return this;
	}
}
