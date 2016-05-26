package com.davfx.ninio.http.v3;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Failable;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.v3.Closing;
import com.davfx.ninio.core.v3.Connecting;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.Listening;
import com.davfx.ninio.core.v3.Receiver;
import com.davfx.ninio.core.v3.SocketBuilder;
import com.davfx.ninio.core.v3.TcpSocketServer.InnerSocketBuilder;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.v3.HttpListeningHandler.ConnectionHandler.ResponseHandler;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;

public final class WebsocketHttpContentReceiver implements HttpContentReceiver {
	private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketHttpContentReceiver.class);

	private final HttpRequest request;
	private final ResponseHandler responseHandler;
	
	private final Closing closing;
	private final Failing failing;
	private final HttpContentReceiver receiver;

	private final SocketBuilder<?> wrappee;
	private final boolean textResponses;
	
	public WebsocketHttpContentReceiver(HttpRequest request, ResponseHandler responseHandler, final boolean textResponses, Listening listening) {
		this.wrappee = wrappee;
		this.textResponses = textResponses;

		String wsKey = null;
		for (String v : request.headers.get("Sec-WebSocket-Key")) {
			wsKey = v;
			break;
		}
		if (wsKey == null) {
			LOGGER.error("Missing Sec-WebSocket-Key header");
			return;
		}
		
		String wsVersion = null;
		for (String v : request.headers.get("Sec-WebSocket-Version")) {
			wsVersion = v;
			break;
		}

		if (!"13".equals(wsVersion)) {
			LOGGER.error("Current implementation does not handle this version: " + wsVersion);
			return;
		}

		HttpResponse response = new HttpResponse(101, "Switching Protocols", ImmutableMultimap.<String, String>builder()
			.put("Connection", "Upgrade")
			.put("Upgrade", "websocket")
			.put("Sec-WebSocket-Accept", BaseEncoding.base64().encode(Hashing.sha1().hashBytes((wsKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(Charsets.UTF_8)).asBytes()))
			.build()
		);

		final HttpContentSender sender = responseHandler.send(response);

		Connector innerConnector = new Connector() {
			@Override
			public void close() {
				sender.send(WebsocketFrameReader.headerOf(true, 0x08, null));
			}
			@Override
			public Connector send(Address address, ByteBuffer buffer) {
				sender.send(WebsocketFrameReader.headerOf(true, textResponses ? 0x01 : 0x02, buffer));
				sender.send(buffer);//TODO join in ONE bb!!!!
				return this;
			}
		};
		
		InnerSocketBuilder builder = new InnerSocketBuilder();
		listening.connecting(request.address, innerConnector, builder);

		Connecting connecting = builder.connecting;
		failing = builder.failing;
		receiver = new WebsocketFrameReader(builder.receiver, builder.closing, sender);

		connecting.connected(innerConnector);
	}

	private static final int PING_MAX_BUFFER_SIZE = 1024;

	@Override
	public void received(ByteBuffer buffer) {
		receiver.received(buffer);
	}
	
	@Override
	public void ended() {
		receiver.ended();
	}
	
	@Override
	public void handle(Address address, ByteBuffer buffer) {
		websocketFrameReader.handle(address, buffer);
	}

	@Override
	public void handle(HttpRequest request) {
		this.request = request;
		websocketFrameReader = new WebsocketFrameReader(new WebsocketFrameReader.Handler() {
			private ByteBuffer pingBuffer = null;
			@Override
			public void failed(IOException e) {
				write.failed(e);
			}
			@Override
			public void handle(int opcode, long frameLength, ByteBuffer partialBuffer) {
				if (opcode == 0x09) {
					if (pingBuffer == null) {
						if (frameLength > PING_MAX_BUFFER_SIZE) {
							write.failed(new IOException("Ping frame is too big: " + frameLength));
							return;
						}
						pingBuffer = ByteBuffer.allocate((int) frameLength);
					}
					
					pingBuffer.put(partialBuffer);
					
					if (pingBuffer.position() == frameLength) {
						pingBuffer.flip();
						write.handle(null, WebsocketFrameReader.headerOf(true, 0x0A, pingBuffer));
						write.handle(null, pingBuffer);
						pingBuffer = null;
					}
					
					return;
				}
				
				if ((opcode == 0x01) || (opcode == 0x02)) {
					wrappee.handle(null, partialBuffer);
					return;
				}

				if (opcode == 0x08) {
					LOGGER.debug("Connection closed");
					write.close();
					wrappee.close();
					return;
				}
				
				write.failed(new IOException("Current implementation does not handle this opcode: " + opcode));
			}
		});
	}
	

	private static final class InnerSocketBuilder implements SocketBuilder<Void> {
		public Receiver receiver = null;
		public Failing failing = null;
		public Connecting connecting = null;
		public Closing closing = null;
		public InnerSocketBuilder() {
		}
		@Override
		public Void receiving(Receiver receiver) {
			this.receiver = receiver;
			return null;
		}
		@Override
		public Void failing(Failing failing) {
			this.failing = failing;
			return null;
		}
		@Override
		public Void connecting(Connecting connecting) {
			this.connecting = connecting;
			return null;
		}
		@Override
		public Void closing(Closing closing) {
			this.closing = closing;
			return null;
		}
	}
	
	private static final class WebsocketFrameReader implements HttpContentReceiver {
		
		public static interface Handler extends Failable {
			void handle(int opcode, long frameLength, ByteBuffer partialBuffer);
		}
		
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
		
		private final Receiver receiver;
		private final Closing closing;

		public WebsocketFrameReader(Receiver receiver, Closing closing, HttpContentSender pingBackSender) {
			this.receiver = receiver;
		}
		
		@Override
		public void handle(Address address, ByteBuffer buffer) {
			while (buffer.hasRemaining()) {
				if (!opcodeRead && buffer.hasRemaining()) {
					int v = buffer.get() & 0xFF;
					if ((v & 0x80) != 0x80) {
						handler.failed(new IOException("Current implementation handles only FIN packets"));
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
					ByteBuffer bb;
					int len = (int) Math.min(buffer.remaining(), currentLen - currentRead);
					if (currentMask == null) {
						bb = buffer.duplicate();
						bb.limit(bb.position() + len);
						buffer.position(buffer.position() + len);
						currentRead += len;
					} else {
						bb = ByteBuffer.allocate(len);
						while (buffer.hasRemaining() && (currentRead < currentLen)) {
							int v = buffer.get() & 0xFF;
							v ^= currentMask[currentPosInMask];
							bb.put((byte) v);
							currentRead++;
							currentPosInMask = (currentPosInMask + 1) % 4;
						}
						bb.flip();
					}
					int o = currentOpcode;
					long l = currentLen;

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
					
					handler.handle(o, l, bb);
				}
			}
		}

		public static ByteBuffer headerOf(boolean fin, int opcode, ByteBuffer buffer) { // No mask
			int extendedPayloadLengthLen;
			if ((buffer == null) || (buffer.remaining() <= 125)) {
				extendedPayloadLengthLen = 0;
			} else if (buffer.remaining() <= 65535) {
				extendedPayloadLengthLen = 2;
			} else {
				extendedPayloadLengthLen = 8;
			}
			ByteBuffer res = ByteBuffer.allocate(2 + extendedPayloadLengthLen + ((buffer == null) ? 0 : buffer.remaining()));
			byte first = (byte) opcode;
			if (fin) {
				first |= 0x80;
			}
			res.put(first);
			if (buffer == null) {
				res.put((byte) 0);
			} else if (buffer.remaining() <= 125) {
				res.put((byte) buffer.remaining());
			} else if (buffer.remaining() <= 65535) {
				res.put((byte) 126);
				res.putShort((short) buffer.remaining());
			} else {
				res.put((byte) 127);
				res.putLong(buffer.remaining());
			}
			res.flip();
			return res;
		}

	}

}
