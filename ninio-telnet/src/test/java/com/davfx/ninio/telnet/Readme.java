package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.ReadyConnection;

public final class Readme {
	public static void main(String[] args) throws Exception {
		final String login = "<your-login>";
		final String password = "<your-password>";
		
		new Telnet().to(new Address("127.0.0.1", Telnet.DEFAULT_PORT)).client().connect(new ReadyConnection() {
			
			private final StringBuilder received = new StringBuilder();
			private FailableCloseableByteBufferHandler write;
			private boolean done = false;

			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
			
			@Override
			public void close() {
				System.out.println("Closed");
			}
			
			private void send(String line) {
				write.handle(null, ByteBuffer.wrap((line + TelnetSpecification.EOL).getBytes(TelnetSpecification.CHARSET)));
			}
			
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				String s = new String(buffer.array(), buffer.position(), buffer.remaining(), TelnetSpecification.CHARSET);
				received.append(s);
				System.out.print(s);
				
				if (received.toString().endsWith("login: ")) {
					received.setLength(0);
					send(login);
				}
				if (received.toString().endsWith("Password:")) {
					received.setLength(0);
					send(password);
				}
				if (!done && received.toString().endsWith(login + "$ ")) {
					received.setLength(0);
					send("echo TEST");
					done = true;
				}
			}
			
			@Override
			public void connected(FailableCloseableByteBufferHandler write) {
				this.write = write;
			}
		});
		
		Thread.sleep(1000);
	}
}
