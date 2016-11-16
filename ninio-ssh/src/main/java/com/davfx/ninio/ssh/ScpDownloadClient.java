package com.davfx.ninio.ssh;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.NinioProvider;
import com.davfx.ninio.core.SendCallback;
import com.google.common.base.Splitter;

public final class ScpDownloadClient implements Connecter {
	private static final Logger LOGGER = LoggerFactory.getLogger(ScpDownloadClient.class);

	public static interface Builder extends NinioBuilder<Connecter> {
		Builder path(String path);
		Builder with(SshClient.Builder builder);
	}

	public static Builder builder() {
		return new Builder() {
			private String path = null;
			private SshClient.Builder builder = null;
			
			@Override
			public Builder path(String path) {
				this.path = path;
				return this;
			}
			
			@Override
			public Builder with(SshClient.Builder builder) {
				this.builder = builder;
				return this;
			}

			@Override
			public Connecter create(NinioProvider ninioProvider) {
				if (path == null) {
					throw new NullPointerException("path");
				}
				if (builder == null) {
					throw new NullPointerException("builder");
				}

				return new ScpDownloadClient(builder.exec("scp -f " + path.replace(" ", "\\ ")).create(ninioProvider));
			}
		};
	}
	
	private final Connecter connecter;
	
	private long size = -1L;
	private long count = 0L;
	private boolean closed = false;

	private ScpDownloadClient(Connecter connecter) {
		this.connecter = connecter;
	}
	
	@Override
	public void connect(final Connection callback) {
		connecter.connect(new Connection() {
			private final SendCallback sendCallback = new SendCallback() {
				@Override
				public void failed(IOException e) {
					if (closed) {
						return;
					}
					
					closed = true;
					callback.failed(e);
				}
				@Override
				public void sent() {
				}
			};
			
			@Override
			public void connected(Address address) {
				connecter.send(null, ByteBuffer.wrap(new byte[] { 0 }), sendCallback);
			}
			
			@Override
			public void received(Address address, ByteBuffer buffer) {
				if (closed) {
					return;
				}

				if (size >= 0L) {
					if (buffer.remaining() > (size - count)) { // Terminated by \0
						buffer.limit(buffer.position() + ((int) (size - count)));
					}
					count += buffer.remaining();
					callback.received(address, buffer);
					if (count == size) {
						closed = true;
						connecter.close();
						callback.closed();
					}
				} else {
					String header = new String(buffer.array(), buffer.position(), buffer.remaining());
					LOGGER.trace("Header: {}", header);
					try {
						size = Long.parseLong(Splitter.on(' ').splitToList(header).get(1));
					} catch (Exception e) {
						closed = true;
						connecter.close();
						callback.failed(new IOException("SCP header corrupted: " + header, e));
					}
					connecter.send(null, ByteBuffer.wrap(new byte[] { 0 }), sendCallback);
				}
			}
			
			@Override
			public void closed() {
				if (closed) {
					return;
				}

				closed = true;
				if (count < size) {
					callback.failed(new IOException("Closed before whole file received"));
				} else {
					callback.closed();
				}
			}
			
			@Override
			public void failed(IOException e) {
				if (closed) {
					return;
				}
				
				closed = true;
				callback.failed(e);
			}
		});
	}
	
	@Override
	public void close() {
		connecter.close();
	}
	
	@Override
	public void send(Address address, ByteBuffer buffer, SendCallback callback) {
		throw new UnsupportedOperationException();
	}
}
