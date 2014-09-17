package com.davfx.ninio.trash;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.OnceByteBufferAllocator;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.QueueListen;
import com.davfx.ninio.common.SocketListen;
import com.davfx.ninio.common.SocketListening;

public final class SocketListenTest {
	public static void main(String[] args) throws Exception {
		Queue queue = new Queue();
		new QueueListen(queue, new SocketListen(queue.getSelector(), new OnceByteBufferAllocator())).listen(new Address("localhost", 8080), new SocketListening() {
			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
			
			@Override
			public CloseableByteBufferHandler connected(Address address, final CloseableByteBufferHandler connection) {
				System.out.println("CONNECTED " + address);
				return new CloseableByteBufferHandler() {
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						byte[] b = new byte[buffer.remaining()];
						buffer.get(b);
						System.out.println("RECEIVED:" + new String(b, Charset.forName("UTF-8")) + "/");
						
						connection.handle(null, ByteBuffer.wrap(b));
					}
					@Override
					public void close() {
						System.out.println("CLIENT CLOSED");
					}
				};
			}
			
			@Override
			public void close() {
				System.out.println("SERVER CLOSED");
			}
		});
	}
}
