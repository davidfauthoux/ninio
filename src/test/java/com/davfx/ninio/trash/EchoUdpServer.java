package com.davfx.ninio.trash;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.DatagramReady;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.OnceByteBufferAllocator;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.QueueReady;
import com.davfx.ninio.common.ReadyConnection;

public final class EchoUdpServer {
	public static void main(String[] args) throws Exception {
		Queue queue = new Queue();
		new QueueReady(queue, new DatagramReady(queue.getSelector(), new OnceByteBufferAllocator())).connect(new Address(8080), new ReadyConnection() {
			private CloseableByteBufferHandler write;
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				byte[] b = new byte[buffer.remaining()];
				buffer.get(b);
				System.out.println("RECEIVED:" + address + " / " + new String(b, Charset.forName("UTF-8")) + "/");
				write.handle(address, ByteBuffer.wrap(b));
			}
			
			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
			
			@Override
			public void connected(FailableCloseableByteBufferHandler write) {
				System.out.println("CONNECTED");
				this.write = write;
			}
			
			@Override
			public void close() {
				System.out.println("CLOSE");
			}
		});
	}
}
