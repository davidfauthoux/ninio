package com.davfx.ninio.ssh;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.ReadyConnection;
import com.google.common.base.Charsets;

///!\ NOT WORKING WITH Java7 ON UP-TO-DATE open-ssl SERVERS

public final class Readme {
	public static void main(String[] args) throws Exception {
		new Ssh()
			.withLogin("<your-login>")
			.withPassword("<your-password>")
		.to(new Address("127.0.0.1", Ssh.DEFAULT_PORT)).client().connect(new ReadyConnection() {
			
			private FailableCloseableByteBufferHandler write;

			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
			
			@Override
			public void close() {
				System.out.println("Closed");
			}
			
			private void send(String line) {
				write.handle(null, ByteBuffer.wrap((line + '\n').getBytes(Charsets.UTF_8)));
			}
			
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
				System.out.print(s);
			}
			
			@Override
			public void connected(FailableCloseableByteBufferHandler write) {
				this.write = write;
				send("echo TEST; ls; top");
			}
		});
		
		Thread.sleep(10000);
	}
}