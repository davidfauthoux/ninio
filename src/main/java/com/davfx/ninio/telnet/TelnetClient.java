package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;

public final class TelnetClient {
	public static final int DEFAULT_PORT = 23;
	public static final String EOL = "\r\n";

	private final TelnetClientConfigurator configurator;

	public TelnetClient(TelnetClientConfigurator configurator) {
		this.configurator = configurator;
	}
	
	public void connect(final TelnetClientHandler clientHandler) {
		configurator.queue.post(new Runnable() {
			@Override
			public void run() {
				Ready ready = configurator.readyFactory.create(configurator.queue);
				ready.connect(configurator.address, new ReadyConnection() {
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
						clientHandler.launched(new TelnetClientHandler.Callback() {
							@Override
							public void close() {
								reader.close();
							}
							
							@Override
							public void send(String line) {
								reader.send(line);
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

	private static final class TelnetResponseReader implements CloseableByteBufferHandler, TelnetClientHandler.Callback {
		private static final Charset USASCII_CHARSET = Charset.forName("US-ASCII");

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

		private final TelnetClientHandler handler;

		public TelnetResponseReader(TelnetClientHandler handler, CloseableByteBufferHandler write) {
			this.handler = handler;
			this.write = write;
		}

		@Override
		public void close() {
			if (!closed) {
				closed = true;
				write.close();
				//%%%%%%%% handler.close();
			}
		}
		
		@Override
		public void send(String line) {
			write.handle(null, ByteBuffer.wrap(line.getBytes(USASCII_CHARSET)));
		}

		private void write(byte response, byte command) {
			write.handle(null, ByteBuffer.wrap(new byte[] { IAC, response, command }));
		}

		@Override
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
				handler.received(r.toString());
			}
		}

	}

}
