package com.davfx.ninio.trash;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.Executors;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.OnceByteBufferAllocator;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.QueueReady;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.common.SocketReady;

public final class SocketReadyTest {
	public static void main(String[] args) throws Exception {
		Queue queue = new Queue();
		new QueueReady(queue, new SocketReady(queue.getSelector(), new OnceByteBufferAllocator())).connect(new Address("localhost", 8080), new ReadyConnection() {
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				byte[] b = new byte[buffer.remaining()];
				buffer.get(b);
				System.out.println("RECEIVED:" + new String(b, Charset.forName("UTF-8")) + "/");
			}
			
			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
			
			@Override
			public void connected(final FailableCloseableByteBufferHandler write) {
				System.out.println("CONNECTED");
				write.handle(null, ByteBuffer.wrap("Hello world\n".getBytes(Charset.forName("UTF-8"))));
				Executors.newCachedThreadPool().execute(new Runnable() {
					@Override
					public void run() {
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
						}
						write.handle(null, ByteBuffer.wrap("Hello world 3\n".getBytes(Charset.forName("UTF-8"))));
					}
				});
				//write.close();
			}
			
			@Override
			public void close() {
				System.out.println("CLOSED");
			}
		});
	}
}
