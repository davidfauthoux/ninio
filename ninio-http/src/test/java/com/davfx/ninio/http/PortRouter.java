package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Listen;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.QueueListen;
import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.ninio.core.SocketListen;
import com.davfx.ninio.core.SocketListening;
import com.davfx.ninio.core.SocketReady;
import com.davfx.ninio.core.SslSocketListening;
import com.davfx.ninio.core.Trust;

public final class PortRouter implements AutoCloseable, Closeable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PortRouter.class);
	
	private static final class InnerCloseableByteBufferHandler implements CloseableByteBufferHandler {
		private FailableCloseableByteBufferHandler write = null;
		private final List<ByteBuffer> toWrite = new LinkedList<>();
		private boolean closed = false;
		
		@Override
		public void handle(Address a, ByteBuffer buffer) {
			if (closed) {
				return;
			}
			
			if (write == null) {
				toWrite.add(buffer);
			} else {
				write.handle(a, buffer);
			}
		}
		
		@Override
		public void close() {
			closed = true;
			toWrite.clear();
			if (write != null) {
				write.close();
			}
		}
	}
	
	private final Queue queue;
	private boolean closed = false;
	private SocketListening.Listening listening = null;
	
	public PortRouter(final Queue queue, final Address bindAddress, final Address toAddress, Trust trust) {
		this.queue = queue;
		SocketListening listening = new SocketListening() {
			@Override
			public void listening(Listening listening) {
				if (closed) {
					LOGGER.trace("Port router internally closed");
					listening.disconnect();
					listening.close();
				} else {
					LOGGER.trace("Port router created");
					PortRouter.this.listening = listening;
				}
			}
			@Override
			public CloseableByteBufferHandler connected(Address clientAddress, final CloseableByteBufferHandler connection) {
				final InnerCloseableByteBufferHandler inner = new InnerCloseableByteBufferHandler(); 
				final Ready ready = new SocketReady(queue.getSelector(), queue.allocator());
				ready.connect(toAddress, new ReadyConnection() {
					@Override
					public void failed(IOException e) {
						connection.close();
					}
					@Override
					public void close() {
						connection.close();
					}
					@Override
					public void handle(Address from, ByteBuffer buffer) {
						connection.handle(from, buffer);
					}
					
					@Override
					public void connected(FailableCloseableByteBufferHandler write) {
						if (inner.closed) {
							write.close();
							return;
						}
						
						inner.write = write;
						for (ByteBuffer b : inner.toWrite) {
							write.handle(toAddress, b);
						}
						inner.toWrite.clear();
					}
				});
				return inner;
			}
			
			@Override
			public void failed(IOException e) {
				LOGGER.error("Could not open port router on: {}", bindAddress, e);
			}
			
			@Override
			public void close() {
				LOGGER.error("Port router closed, was open on: {}", bindAddress);
			}
		};
		
		if (trust != null) {
			listening = new SslSocketListening(trust, queue.allocator(), listening);
		}
		
		Listen listen = new SocketListen(queue.getSelector(), queue.allocator());
		listen = new QueueListen(queue, listen);
		listen.listen(bindAddress, listening);
	}
	
	@Override
	public void close() {
		LOGGER.trace("Closing port router");
		queue.post(new Runnable() {
			@Override
			public void run() {
				closed = true;
				if (listening != null) {
					LOGGER.trace("Port router closed");
					listening.disconnect();
					listening.close();
				}
			}
		});
	}
}
