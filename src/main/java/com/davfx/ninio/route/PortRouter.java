package com.davfx.ninio.route;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Listen;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.QueueListen;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.common.SocketListen;
import com.davfx.ninio.common.SocketListening;
import com.davfx.ninio.common.SocketReady;
import com.davfx.ninio.common.SslSocketListening;
import com.davfx.ninio.common.Trust;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class PortRouter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PortRouter.class);
	
	private static final Config CONFIG = ConfigFactory.load();

	public static void main(String[] args) throws Exception {
		new PortRouter(new Queue(),
			new Address(CONFIG.getString("route.bind.host"), CONFIG.getInt("route.bind.port")),
			new Address(CONFIG.getString("route.to.host"), CONFIG.getInt("route.to.port")),
			null
		);
	}
	

	
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
	
	public PortRouter(final Queue queue, final Address bindAddress, final Address toAddress, Trust trust) {
		SocketListening listening = new SocketListening() {
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
}
