package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.ByteBufferAllocator;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.OnceByteBufferAllocator;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.common.SocketReadyFactory;

public final class TelnetClient implements TelnetConnector {
	public static final int DEFAULT_PORT = 23;
	public static final String EOL = "\r\n";

	private Queue queue = null;
	private Address address = new Address("localhost", DEFAULT_PORT);
	private String host = null;
	private int port = -1;

	private ReadyFactory readyFactory = new SocketReadyFactory();

	public TelnetClient() {
	}
	
	@Override
	public String getEol() {
		return EOL;
	}
	
	@Override
	public TelnetClient withQueue(Queue queue) {
		this.queue = queue;
		return this;
	}
	
	@Override
	public TelnetClient withHost(String host) {
		this.host = host;
		return this;
	}
	@Override
	public TelnetClient withPort(int port) {
		this.port = port;
		return this;
	}
	@Override
	public TelnetClient withAddress(Address address) {
		this.address = address;
		return this;
	}
	
	@Override
	public TelnetClient override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
	
	@Override
	public void connect(final TelnetClientHandler clientHandler) {
		final Queue q;
		final boolean shouldCloseQueue;
		if (queue == null) {
			try {
				q = new Queue();
			} catch (IOException e) {
				clientHandler.failed(e);
				return;
			}
			shouldCloseQueue = true;
		} else {
			q = queue;
			shouldCloseQueue = false;
		}

		final Address a;
		if (host != null) {
			if (port < 0) {
				a = new Address(host, address.getPort());
			} else {
				a = new Address(host, port);
			}
		} else {
			a = address;
		}

		q.post(new Runnable() {
			@Override
			public void run() {
				ByteBufferAllocator allocator = new OnceByteBufferAllocator();
				Ready ready = readyFactory.create(q, allocator);
				ready.connect(a, new ReadyConnection() {
					private TelnetResponseReader reader = null;
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						reader.handle(address, buffer);
					}
					
					@Override
					public void failed(IOException e) {
						if (shouldCloseQueue) {
							q.close();
						}
						clientHandler.failed(e);
					}
					
					@Override
					public void connected(FailableCloseableByteBufferHandler write) {
						reader = new TelnetResponseReader(clientHandler, write);
						clientHandler.launched(new TelnetClientHandler.Callback() {
							@Override
							public void close() {
								reader.close();
								if (shouldCloseQueue) {
									q.close();
								}
							}
							
							@Override
							public void send(String line) {
								reader.send(line);
							}
						});
					}
					
					@Override
					public void close() {
						if (shouldCloseQueue) {
							q.close();
						}
						reader.close();
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
				handler.close();
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
