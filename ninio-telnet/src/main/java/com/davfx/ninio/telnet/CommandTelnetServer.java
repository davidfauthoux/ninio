package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.SocketListening;
import com.google.common.base.Function;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class CommandTelnetServer implements AutoCloseable, Closeable {

	private static final Logger LOGGER = LoggerFactory.getLogger(CommandTelnetServer.class);

	private static final Config CONFIG = ConfigFactory.load(CommandTelnetServer.class.getClassLoader());

	private static final int BUFFERING_LIMIT = CONFIG.getBytes("ninio.telnet.sharing.limit").intValue();

	private final Queue queue;
	private boolean closed = false;
	private Closeable listening = null;
	
	public CommandTelnetServer(Queue queue, Address address, final String header, final String cut, final Function<String, String> commandHandler) {
		this.queue = queue;
		new TelnetServer(queue, address, new SocketListening() {
			
			@Override
			public void failed(IOException e) {
				LOGGER.error("Failed", e);
			}
			
			@Override
			public void close() {
				LOGGER.debug("Closed");
			}
			
			@Override
			public void listening(Closeable listening) {
				if (closed) {
					listening.close();
				} else {
					LOGGER.trace("Listening");
					CommandTelnetServer.this.listening = listening;
				}
			}
			
			@Override
			public CloseableByteBufferHandler connected(Address address, final CloseableByteBufferHandler connection) {
				LOGGER.trace("Connected: {}", address);
				connection.handle(address, ByteBuffer.wrap(header.getBytes(TelnetSpecification.CHARSET)));
				
				return new CuttingByteBufferHandler(BUFFERING_LIMIT, new FailableCloseableByteBufferHandler() {
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Failed", e);
						connection.close();
					}
					
					@Override
					public void close() {
						connection.close();
					}
					
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						// LOGGER.debug("Handle: /{}/{}/", new String(buffer.array(), buffer.position(), buffer.remaining(), TelnetSpecification.CHARSET), new String(buffer.array(), buffer.position(), buffer.remaining() - cut.length(), TelnetSpecification.CHARSET));
						String result = commandHandler.apply(new String(buffer.array(), buffer.position(), buffer.remaining() - cut.length(), TelnetSpecification.CHARSET));
						if (result == null) {
							connection.close();
							return;
						}
						connection.handle(address, ByteBuffer.wrap(result.getBytes(TelnetSpecification.CHARSET)));
					}
				}).setPrompt(ByteBuffer.wrap(cut.getBytes(TelnetSpecification.CHARSET)));
			}
		});
	}
	
	@Override
	public void close() {
		queue.post(new Runnable() {
			@Override
			public void run() {
				LOGGER.trace("Closing");
				closed = true;
				if (listening != null) {
					listening.close();
				}
			}
		});
	}
}
