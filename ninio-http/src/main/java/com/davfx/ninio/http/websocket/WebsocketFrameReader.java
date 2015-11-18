package com.davfx.ninio.http.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferHandler;
import com.davfx.ninio.core.Failable;

final class WebsocketFrameReader implements ByteBufferHandler {
	
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
	
	private final Handler handler;

	public WebsocketFrameReader(Handler handler) {
		this.handler = handler;
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
