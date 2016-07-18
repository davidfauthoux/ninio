package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferAllocator;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.NopConnecterConnectingCallback;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.core.TcpSocket;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.BaseEncoding;

public final class WebsocketSocket implements Connecter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketSocket.class);

	public static interface Builder extends TcpSocket.Builder {
		Builder to(Address connectAddress);
		Builder with(HttpClient httpClient);
		Builder route(String path);
	}

	public static Builder builder() {
		return new Builder() {
			private HttpClient httpClient = null;
			private String path = String.valueOf(HttpSpecification.PATH_SEPARATOR);
			
			private Address connectAddress = null;
			
			@Override
			public TcpSocket.Builder with(ByteBufferAllocator byteBufferAllocator) {
				return this;
			}
			
			@Override
			public TcpSocket.Builder bind(Address bindAddress) {
				return this;
			}
			
			@Override
			public Builder to(Address connectAddress) {
				this.connectAddress = connectAddress;
				return this;
			}
			
			@Override
			public Builder with(HttpClient httpClient) {
				this.httpClient = httpClient;
				return this;
			}
			
			@Override
			public Builder route(String path) {
				this.path = path;
				return this;
			}
			
			@Override
			public Connecter create(Queue queue) {
				if (httpClient == null) {
					throw new NullPointerException("httpClient");
				}
				return new WebsocketSocket(queue, httpClient, path, connectAddress);
			}
		};
	}

	private static final SecureRandom RANDOM = new SecureRandom();
	
	private final Queue queue;
	private final HttpContentSender sender;
	private Connection connection = null;
	private boolean closed = false;
	
	private WebsocketSocket(final Queue queue, HttpClient httpClient, String path, final Address connectAddress) {
		this.queue = queue;
		
		HttpRequest request = new HttpRequest(connectAddress, false, HttpMethod.GET, path, ImmutableMultimap.<String, String>builder()
			.put("Sec-WebSocket-Key", BaseEncoding.base64().encode(String.valueOf(RANDOM.nextLong()).getBytes(Charsets.UTF_8)))
			.put("Sec-WebSocket-Version", "13")
			.put("Connection", "Upgrade")
			.put("Upgrade", "websocket")
			.build()
		);

		HttpRequestBuilder b = httpClient.request();
		sender = b.build(request);
		b.receive(new HttpReceiver() {
			private boolean opcodeRead = false;
			private int currentOpcode;
			private boolean lenRead = false;
			private boolean mustReadExtendedLen16;
			private boolean mustReadExtendedLen64;
			private long currentLen;
			private long currentRead;
			private boolean mustReadMask;
			private ByteBuffer currentExtendedLenBuffer;
			private byte[] currentMask;
			private ByteBuffer currentMaskBuffer;
			private int currentPosInMask;
			
			private long toPing = 0L;

			@Override
			public HttpContentReceiver received(final HttpResponse response) {
				// We should check everything here (status code, header Sec-WebSocket-Accept, ...)
				if (response.status != 101) {
					queue.execute(new Runnable() {
						@Override
						public void run() {
							if (closed) {
								return;
							}
							if (connection != null) {
								closed = true;
								connection.failed(new IOException("[" + response.status + " " + response.reason + "]"));
							}
						}
					});
					return null;
				}
				
				return new HttpContentReceiver() {
					@Override
					public void received(ByteBuffer buffer) {
						SendCallback sendCallback = new SendCallback() {
							@Override
							public void failed(IOException e) {
								sender.cancel();
							}
							@Override
							public void sent() {
							}
						};
						
						while (buffer.hasRemaining()) {
							if (!opcodeRead && buffer.hasRemaining()) {
								int v = buffer.get() & 0xFF;
								if ((v & 0x80) != 0x80) {
									LOGGER.error("Current implementation handles only FIN packets");
									sender.cancel();
									connection.closed();
									return;
								}
								currentOpcode = v & 0x0F;
								opcodeRead = true;
							}
					
							if (!lenRead && buffer.hasRemaining()) {
								int v = buffer.get() & 0xFF;
								int len = v & 0x7F;
								if (len <= 125) {
									currentLen = len;
									mustReadExtendedLen16 = false;
									mustReadExtendedLen64 = false;
								} else if (len == 126) {
									mustReadExtendedLen16 = true;
									mustReadExtendedLen64 = false;
									currentExtendedLenBuffer = ByteBuffer.allocate(2);
								} else {
									mustReadExtendedLen64 = true;
									mustReadExtendedLen16 = false;
									currentExtendedLenBuffer = ByteBuffer.allocate(8);
								}
								mustReadMask = ((v & 0x80) == 0x80);
								if (mustReadMask) {
									currentMask = new byte[4];
									currentMaskBuffer = ByteBuffer.wrap(currentMask);
									currentPosInMask = 0;
								}
								lenRead = true;
							}
							
							while (mustReadExtendedLen16 && buffer.hasRemaining()) {
								int v = buffer.get();
								currentExtendedLenBuffer.put((byte) v);
								if (currentExtendedLenBuffer.position() == 2) {
									currentExtendedLenBuffer.flip();
									currentLen = currentExtendedLenBuffer.getShort() & 0xFFFF;
									mustReadExtendedLen16 = false;
									currentExtendedLenBuffer = null;
								}
							}
							while (mustReadExtendedLen64 && buffer.hasRemaining()) {
								int v = buffer.get();
								currentExtendedLenBuffer.put((byte) v);
								if (currentExtendedLenBuffer.position() == 8) {
									currentExtendedLenBuffer.flip();
									currentLen = currentExtendedLenBuffer.getLong();
									mustReadExtendedLen64 = false;
									currentExtendedLenBuffer = null;
								}
							}
							while (mustReadMask && buffer.hasRemaining()) {
								int v = buffer.get();
								currentMaskBuffer.put((byte) v);
								if (currentMaskBuffer.position() == 4) {
									currentMaskBuffer = null;
									mustReadMask = false;
								}
							}
							
							if (opcodeRead && lenRead && !mustReadExtendedLen16 && !mustReadExtendedLen64 && !mustReadMask && buffer.hasRemaining() && (currentRead < currentLen)) {
								final ByteBuffer partialBuffer;
								int len = (int) Math.min(buffer.remaining(), currentLen - currentRead);
								if (currentMask == null) {
									partialBuffer = buffer.duplicate();
									partialBuffer.limit(partialBuffer.position() + len);
									buffer.position(buffer.position() + len);
									currentRead += len;
								} else {
									partialBuffer = ByteBuffer.allocate(len);
									while (buffer.hasRemaining() && (currentRead < currentLen)) {
										int v = buffer.get() & 0xFF;
										v ^= currentMask[currentPosInMask];
										partialBuffer.put((byte) v);
										currentRead++;
										currentPosInMask = (currentPosInMask + 1) % 4;
									}
									partialBuffer.flip();
								}
								int opcode = currentOpcode;
								long frameLength = currentLen;

								if (currentRead == currentLen) {
									opcodeRead = false;
									lenRead = false;
									mustReadExtendedLen16 = false;
									mustReadExtendedLen64 = false;
									currentExtendedLenBuffer = null;
									mustReadMask = false;
									currentMaskBuffer = null;
									currentMask = null;
									currentRead = 0L;
								}
								
								if (opcode == 0x09) {
									if (toPing == 0L) {
										toPing = frameLength;
										sender.send(WebsocketUtils.headerOf(0x0A, frameLength), sendCallback);
									}

									toPing -= partialBuffer.remaining();
									sender.send(partialBuffer, sendCallback);
								} else if ((opcode == 0x01) || (opcode == 0x02)) {
									queue.execute(new Runnable() {
										@Override
										public void run() {
											if (closed) {
												return;
											}
											if (connection != null) {
												connection.received(null, partialBuffer);
											}
										}
									});
								} else if (opcode == 0x08) {
									LOGGER.debug("Connection closed by peer");
									sender.cancel();
									queue.execute(new Runnable() {
										@Override
										public void run() {
											if (closed) {
												return;
											}
											if (connection != null) {
												closed = true;
												connection.closed();
											}
										}
									});
									break;
								}
							}
						}
					}
					
					@Override
					public void ended() {
						LOGGER.debug("Connection abruptly closed by peer");
						sender.cancel();
						queue.execute(new Runnable() {
							@Override
							public void run() {
								if (closed) {
									return;
								}
								if (connection != null) {
									closed = true;
									connection.closed();
								}
							}
						});
					}
				};
			}
			
			@Override
			public void failed(final IOException ioe) {
				queue.execute(new Runnable() {
					@Override
					public void run() {
						if (closed) {
							return;
						}
						if (connection != null) {
							closed = true;
							connection.failed(ioe);
						}
					}
				});
			}
		});
	}

	@Override
	public void close() {
		LOGGER.trace("Close requested");
		sender.send(WebsocketUtils.headerOf(0x08, 0L), new NopConnecterConnectingCallback());

		queue.execute(new Runnable() {
			@Override
			public void run() {
				closed = true;
			}
		});
	}
	
	@Override
	public void send(Address address, ByteBuffer buffer, final SendCallback callback) {
		sender.send(WebsocketUtils.headerOf(0x02, buffer.remaining()), new NopConnecterConnectingCallback()); //TODO ENCHAINER!!! ne pas assume qu'un send fail est forcement suivi de fail pour les autres send
		sender.send(buffer, new SendCallback() {
			@Override
			public void failed(final IOException e) {
				queue.execute(new Runnable() {
					@Override
					public void run() {
						callback.failed(e);
					}
				});
			}
			
			@Override
			public void sent() {
				queue.execute(new Runnable() {
					@Override
					public void run() {
						if (closed) {
							return;
						}
						callback.sent();
					}
				});
			}
		});
	}
	
	@Override
	public void connect(final Connection callback) {
		queue.execute(new Runnable() {
			@Override
			public void run() {
				if (closed) {
					callback.failed(new IOException("Closed"));
				}
				connection = callback;
				callback.connected(null);
			}
		});
		sender.finish(); //TODO check if working with websocket
	}
}
