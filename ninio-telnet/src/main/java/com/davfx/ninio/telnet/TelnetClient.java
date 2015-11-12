package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.ninio.core.ReadyFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class TelnetClient implements TelnetReady {

	private static final Config CONFIG = ConfigFactory.load(TelnetClient.class.getClassLoader());
	
	public static final String EOL = "\r\n";
	public static final Charset CHARSET = Charset.forName(CONFIG.getString("ninio.telnet.charset"));

	private final Queue queue;
	private final ReadyFactory readyFactory;
	private final Address address;

	public TelnetClient(Queue queue, ReadyFactory readyFactory, Address address) {
		this.queue = queue;
		this.readyFactory = readyFactory;
		this.address = address;
	}
	
	@Override
	public void connect(final ReadyConnection clientHandler) {
		queue.post(new Runnable() {
			@Override
			public void run() {
				Ready ready = readyFactory.create(queue);
				ready.connect(address, new ReadyConnection() {
					private TelnetResponseReader reader = null;
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						reader.handle(address, buffer);
					}
					
					@Override
					public void failed(IOException e) {
						clientHandler.failed(e);
					}
					
					@Override
					public void connected(FailableCloseableByteBufferHandler write) {
						reader = new TelnetResponseReader(clientHandler, write);
						clientHandler.connected(new FailableCloseableByteBufferHandler() {
							@Override
							public void failed(IOException e) {
								reader.doClose();
							}
							
							@Override
							public void close() {
								reader.doClose();
							}
							
							@Override
							public void handle(Address address, ByteBuffer buffer) {
								reader.doHandle(address, buffer);
							}
						});
					}
					
					@Override
					public void close() {
						clientHandler.close();
					}
				});
			}
		});
	}

	private static final class TelnetResponseReader {
		private static enum State {
			NONE,
			IAC,
			COMMAND,
			SUBCOMMAND,
			SUBCOMMAND_IAC,
		}

		public static final byte DONT = (byte) 254;
		public static final byte DO = (byte) 253;
		public static final byte WONT = (byte) 252;
		public static final byte WILL = (byte) 251;
		public static final byte IAC = (byte) 255;
		public static final byte SB = (byte) 250;
		public static final byte SE = (byte) 240;
		public static final byte ECHO = (byte) 1;

		private boolean closed = false;
		private State readingCommand = State.NONE;
		private int lastRequest;
		private final StringBuilder subcommandLine = new StringBuilder();

		// private byte subCommandCode;

		private final CloseableByteBufferHandler write;

		private final ReadyConnection handler;

		public TelnetResponseReader(ReadyConnection handler, CloseableByteBufferHandler write) {
			this.handler = handler;
			this.write = write;
		}

		public void doClose() {
			if (!closed) {
				closed = true;
				write.close();
			}
		}
		
		public void doHandle(Address address, ByteBuffer buffer) {
			write.handle(address, buffer);
		}

		private void write(byte response, byte command) {
			write.handle(null, ByteBuffer.wrap(new byte[] { IAC, response, command }));
		}

		public void handle(Address address, ByteBuffer buffer) {
			if (closed) {
				return;
			}

			StringBuilder r = new StringBuilder();
			while (buffer.hasRemaining()) {
				byte b = buffer.get();
				switch (readingCommand) {
					case SUBCOMMAND:
						if (b == IAC) {
							readingCommand = State.SUBCOMMAND_IAC;
						} else {
							subcommandLine.append((char) b);
						}
						break;
					case SUBCOMMAND_IAC:
						if (b == IAC) {
							subcommandLine.append((char) b);
							readingCommand = State.SUBCOMMAND;
						} else if (b == SE) {
							subcommandLine.setLength(0);
							readingCommand = State.NONE;
						} else {
							closed = true;
							write.close();
							handler.failed(new IOException("Missing SE"));
						}
						break;
					case NONE:
						if (b == IAC) {
							readingCommand = State.IAC;
						} else {
							r.append((char) b);
						}
						break;
					case IAC:
						if (b == IAC) {
							r.append((char) b);
							readingCommand = State.NONE;
						} else {
							lastRequest = b;
							readingCommand = State.COMMAND;
						}
						break;
					case COMMAND:
						if (lastRequest == SB) {
							// subCommandCode = b;
							readingCommand = State.SUBCOMMAND;
						} else {
							if (lastRequest == DO) {
								write(WONT, b);
							} else if (lastRequest == WILL) {
								if (b == ECHO) {
									write(DO, b);
								} else {
									write(DONT, b);
								}
								/*
								} else if (lastRequest == DONT) {
									write(WONT, b);
								} else if (lastRequest == WONT) {
									write(DONT, b);
								*/
							} else {
								// Ignored
							}
							readingCommand = State.NONE;
						}
						break;
				}
			}

			if (r.length() > 0) {
				handler.handle(address, ByteBuffer.wrap(r.toString().getBytes(CHARSET)));
			}
		}
	}

}
