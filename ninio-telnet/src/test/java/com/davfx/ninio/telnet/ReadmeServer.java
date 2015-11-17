package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.SocketListening;

public final class ReadmeServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(ReadmeServer.class);
	public static void main(String[] args) throws Exception {
		new TelnetServer(new Queue(), new Address("127.0.0.1", 8080), new SocketListening() {
			
			@Override
			public void failed(IOException e) {
				LOGGER.error("Failed", e);
			}
			
			@Override
			public void close() {
				LOGGER.debug("Closed");
			}
			
			@Override
			public void listening(Closeable closeable) {
				LOGGER.debug("Listening");
			}
			
			@Override
			public CloseableByteBufferHandler connected(Address address, final CloseableByteBufferHandler connection) {
				LOGGER.debug("Connected: {}", address);
				connection.handle(address, ByteBuffer.wrap(("Alright!" + TelnetSpecification.EOL).getBytes(TelnetSpecification.CHARSET)));
				return new CloseableByteBufferHandler() {
					
					@Override
					public void close() {
						connection.close();
					}
					
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						connection.handle(address, ByteBuffer.wrap(("echo " + new String(buffer.array(), buffer.position(), buffer.remaining(), TelnetSpecification.CHARSET)).getBytes(TelnetSpecification.CHARSET)));
					}
				};
			}
		});
		
		Thread.sleep(1000000);
	}
}
