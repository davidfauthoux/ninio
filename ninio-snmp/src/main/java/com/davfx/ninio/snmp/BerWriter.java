package com.davfx.ninio.snmp;

import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;

public final class BerWriter {
	public static final int BUFFER_SIZE = 8 * 1024; // 8 KiB MAX

	private ByteBuffer buffer;
	private final Deque<ByteBuffer> buffers = new LinkedList<ByteBuffer>();

	// private final Deque<Integer> toWriteLengthPositions = new LinkedList<Integer>();

	public BerWriter(ByteBuffer buffer) {
		this.buffer = buffer;
	}

	private void pushBuffer() {
		ByteBuffer b = ByteBuffer.allocate(BUFFER_SIZE);
		buffers.addFirst(buffer);
		buffer = b;
	}

	private void popBuffer() {
		buffer = buffers.removeFirst();
	}

	// Could be encoded on less bytes than 4 but may complicate the algorithm a lot
	private void willWriteLength() {
		/*
		buffer.put((byte) (4 | BerConstants.ASN_BIT8)); // High bit to identify long length
		toWriteLengthPositions.addFirst(buffer.position());
		buffer.putInt(0);
		*/

		pushBuffer();
	}

	private void doWriteLength() {
		/*
		int old = buffer.position();
		int position = toWriteLengthPositions.removeFirst();
		int length = old - position - 4;
		buffer.position(position);
		buffer.order(ByteOrder.BIG_ENDIAN);
		buffer.putInt(length);
		*/

		ByteBuffer toWrite = buffer;
		popBuffer();
		int length = toWrite.position();
		if (length < 0x80) {
			buffer.put((byte) length);
		} else {
			int mask = 0xFF;
			int bits = 0;
			int count = 0;

			while (true) {
				mask <<= 8;
				count++;
				if ((length & mask) == 0) {
					break;
				}
				bits += 8;
			}

			buffer.put((byte) (BerConstants.ASN_BIT8 | count)); // Number of bytes

			while (bits >= 0) {
				int b = (length >>> bits) & 0xFF;
				buffer.put((byte) b);
				bits -= 8;
			}
		}

		// Not very optimal... but avoid to calculate length before writing something
		buffer.put(toWrite.array(), 0, toWrite.position());
	}

	public void writeInteger(int value) {
		buffer.put((byte) BerConstants.INTEGER);
		willWriteLength();

		int mask = 0x1FF << ((8 * 3) - 1);
		int intsize = 4;
		while ((((value & mask) == 0) || ((value & mask) == mask)) && intsize > 1) {
			intsize--;
			value <<= 8;
		}
		mask = 0xFF << (8 * 3);
		while (intsize > 0) {
			buffer.put((byte) ((value & mask) >>> (8 * 3)));
			value <<= 8;
			intsize--;
		}

		/*
		buffer.order(ByteOrder.BIG_ENDIAN);
		buffer.putInt(value);
		*/

		doWriteLength();
	}

	public void writeOid(Oid oid) {
		buffer.put((byte) BerConstants.OID);
		willWriteLength();

		int[] raw = oid.getRaw();

		if (raw.length < 2) {
			throw new IllegalArgumentException();
		}

		buffer.put((byte) ((raw[1] + (raw[0] * 40)) & 0xFF));

		for (int i = 2; i < raw.length; i++) {
			int value = raw[i];

			int mask = ~0x80;
			int bits = 0;

			while (true) {
				mask <<= 7;
				if ((value & mask) == 0) {
					break;
				}
				bits += 7;
			}

			while (bits >= 0) {
				int b = (value >>> bits) & ~0x80;
				if (bits > 0) {
					b |= 0x80; // Continuation bit
				}
				buffer.put((byte) b);
				bits -= 7;
			}
		}

		doWriteLength();
	}

	public void writeString(ByteBuffer s) {
		buffer.put((byte) BerConstants.OCTETSTRING);
		willWriteLength();
		buffer.put(s);
		doWriteLength();
	}

	public void beginWriteSequence() {
		buffer.put((byte) BerConstants.SEQUENCE);
		willWriteLength();
	}

	public void endWriteSequence() {
		doWriteLength();
	}

	public void beginWriteRequestPdu(int type) {
		buffer.put((byte) type);
		willWriteLength();
	}

	public void endWriteRequestPdu() {
		doWriteLength();
	}

	public void beginWriteResponsePdu() {
		buffer.put((byte) BerConstants.RESPONSE);
		willWriteLength();
	}

	public void endWriteResponsePdu() {
		doWriteLength();
	}

	public void writeNull() {
		buffer.put((byte) BerConstants.NULL);
		willWriteLength();
		doWriteLength();
	}
}
