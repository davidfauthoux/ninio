package com.davfx.ninio.ssh;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Connector;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.Receiver;
import com.google.common.base.Splitter;

public final class ScpDownloadClient implements Receiver, Connecting, Closing {
	private static final Logger LOGGER = LoggerFactory.getLogger(ScpDownloadClient.class);

	public static interface Builder {
		Builder failing(Failing failing);
		Builder closing(Closing closing);
		Builder receiving(Receiver receiver);
		Builder path(String path);
		
		SshClient.Builder build(SshClient.Builder builder);
	}

	public static Builder builder() {
		return new Builder() {
			private Receiver receiver = null;
			private Closing closing = null;
			private Failing failing = null;
			private String path = null;
			
			@Override
			public Builder path(String path) {
				this.path = path;
				return this;
			}

			@Override
			public Builder closing(Closing closing) {
				this.closing = closing;
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
			public SshClient.Builder build(SshClient.Builder builder) {
				if (builder == null) {
					throw new NullPointerException("builder");
				}

				ScpDownloadClient c = new ScpDownloadClient(receiver, closing, failing);
				return builder.exec("scp -f " + path.replace(" ", "\\ ")).receiving(c).connecting(c).closing(c);
			}
		};
	}
	
	private final Receiver receiver;
	private final Closing closing;
	private final Failing failing;
	
	private long size = -1L;
	private long count = 0L;
	private boolean closed = false;

	private ScpDownloadClient(Receiver receiver, Closing closing, Failing failing) {
		this.receiver = receiver;
		this.closing = closing;
		this.failing = failing;
	}
	
	@Override
	public void closed() {
		if (closed) {
			return;
		}
		closed = true;
		if (count < size) {
			failing.failed(new IOException("Closed before whole file received"));
		} else {
			closing.closed();
		}
	}
	
	@Override
	public void received(Connector conn, Address address, ByteBuffer buffer) {
		if (closed) {
			return;
		}

		if (size >= 0L) {
			if (buffer.remaining() > (size - count)) { // Terminated by \0
				buffer.limit(buffer.position() + ((int) (size - count)));
			}
			count += buffer.remaining();
			receiver.received(null, address, buffer);
			if (count == size) {
				closed = true;
				conn.close();
				closing.closed();
			}
		} else {
			String header = new String(buffer.array(), buffer.position(), buffer.remaining());
			LOGGER.trace("Header: {}", header);
			try {
				size = Long.parseLong(Splitter.on(' ').splitToList(header).get(1));
			} catch (Exception e) {
				closed = true;
				conn.close();
				failing.failed(new IOException("SCP header corrupted: " + header, e));
			}
			conn.send(null, ByteBuffer.wrap(new byte[] { 0 }));
		}
	}

	@Override
	public void connected(Connector conn, Address address) {
		conn.send(null, ByteBuffer.wrap(new byte[] { 0 }));
	}
}
