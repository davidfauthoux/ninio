package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connected;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.Listening;
import com.davfx.ninio.core.Nop;
import com.davfx.ninio.core.SendCallback;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.BaseEncoding;

public final class WebsocketHttpContentReceiver implements HttpContentReceiver {
	private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketHttpContentReceiver.class);

	private final HttpContentReceiver receiver;

	private static byte[] sha1(byte[] b) {
		try {
			MessageDigest crypt = MessageDigest.getInstance("SHA-1");
			crypt.reset();
			crypt.update(b);
			return crypt.digest();
		} catch (Exception e) {
			return new byte[] {};
		}
	}
	
	public WebsocketHttpContentReceiver(HttpRequest request, HttpListeningHandler.HttpResponseSender responseHandler, final boolean textResponses, Listening listening) {
		String wsKey = null;
		for (String v : request.headers.get("Sec-WebSocket-Key")) {
			wsKey = v;
			break;
		}
		if (wsKey == null) {
			LOGGER.error("Missing Sec-WebSocket-Key header");
			responseHandler.send(HttpResponse.badRequest());
			receiver = null;
			return;
		}
		
		String wsVersion = null;
		for (String v : request.headers.get("Sec-WebSocket-Version")) {
			wsVersion = v;
			break;
		}

		if (!"13".equals(wsVersion)) {
			LOGGER.error("Current implementation does not handle this version: " + wsVersion);
			responseHandler.send(HttpResponse.badRequest());
			receiver = null;
			return;
		}

		HttpResponse response = new HttpResponse(101, "Switching Protocols", ImmutableMultimap.<String, String>builder()
			.put("Connection", "Upgrade")
			.put("Upgrade", "websocket")
			.put("Sec-WebSocket-Accept", BaseEncoding.base64().encode(sha1((wsKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(Charsets.UTF_8))))
			.build()
		);

		final HttpContentSender sender = responseHandler.send(response);

		Connection connection = listening.connecting(new Connected() {
			//%% private final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
			
			@Override
			public void close() {
				// sender.send(WebsocketUtils.headerOf(0x08, 0L));
				LOGGER.trace("Close requested");
				sender.cancel();
			}
			
			@Override
			public void send(Address address, ByteBuffer buffer, SendCallback callback) {
				/*%%
				deflater.reset();
				deflater.setInput(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
				deflater.finish();
				List<ByteBuffer> buffers = new LinkedList<>();
				long len = 0L;
				while (true) {
					byte[] b = new byte[1024];
					int l = deflater.deflate(b, 0, b.length);
					if (l == 0) {
						break;
					}
					buffers.add(ByteBuffer.wrap(b, 0, l));
					len += l;
				}
				
				sender.send(headerOf(textResponses ? 0x01 : 0x02, len));
				for (ByteBuffer bb : buffers) {
					sender.send(bb);
				}
				*/
				
				sender.send(WebsocketUtils.headerOf(textResponses ? 0x01 : 0x02, buffer.remaining()), new Nop());
				sender.send(buffer, callback);
			}
		});
		
		connection.connected(null);

		receiver = new WebsocketFrameReader(connection, sender);
	}

	@Override
	public void received(ByteBuffer buffer) {
		if (receiver != null) {
			receiver.received(buffer);
		}
	}
	
	@Override
	public void ended() {
		if (receiver != null) {
			receiver.ended();
		}
	}
	
	private static final class WebsocketFrameReader implements HttpContentReceiver {
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

		private final Connection connection;
		private final HttpContentSender sender;
		
		public WebsocketFrameReader(Connection connection, HttpContentSender sender) {
			this.connection = connection;
			this.sender = sender;
		}
		
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
				
				if (opcodeRead && lenRead && !mustReadExtendedLen16 && !mustReadExtendedLen64 && !mustReadMask) { //%% && buffer.hasRemaining() && (currentRead < currentLen)) {
					ByteBuffer partialBuffer;
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
						connection.received(null, partialBuffer);
					} else if (opcode == 0x08) {
						LOGGER.trace("Connection requested by peer");
						sender.cancel();
						connection.closed();
						break;
					}
				}
			}
		}
		
		@Override
		public void ended() {
			LOGGER.trace("Connection closed");
			sender.cancel();
		}
	}
}
