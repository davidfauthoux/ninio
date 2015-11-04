package com.davfx.ninio.snmp;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import com.google.common.io.BaseEncoding;

public final class BerReader {
	private final ByteBuffer buffer;
	private final Deque<Integer> toReadLengthPositions = new LinkedList<Integer>();

	public BerReader(ByteBuffer buffer) {
		this.buffer = buffer;
	}

	private int doReadType(ByteBuffer buffer) throws IOException {
		int b = buffer.get() & 0xFF;
		if ((b & BerConstants.ASN_BIT8) == BerConstants.ASN_BIT8) {
			throw new IOException("No such object");
		}
		return b;
	}

	// Strict reading

	// checked
	private static int doReadLength(ByteBuffer buffer) throws IOException {
		int lengthbyte = buffer.get() & 0xFF;

		if ((lengthbyte & BerConstants.ASN_BIT8) == BerConstants.ASN_BIT8) {
			lengthbyte &= ~BerConstants.ASN_BIT8;
			if (lengthbyte == 0) {
				throw new IOException("Indefinite lengths are not supported");
			}
			if (lengthbyte > 4) {
				throw new IOException("Data length > 4 bytes are not supported");
			}
			int length = 0;
			for (int i = 0; i < lengthbyte; i++) {
				length <<= 8;
				length |= buffer.get() & 0xFF;
			}
			if (length < 0) {
				throw new IOException("SNMP does not support data lengths > 2^31");
			}
			return length;
		} else { // Short asnlength
			return lengthbyte;
		}
	}

	private static int doReadInteger(ByteBuffer buffer, int length) throws IOException {
		int value = 0;
		for (int i = 0; i < length; i++) {
			int b = buffer.get() & 0xFF;
			if ((i == 0) && ((b & 0x80) == 0x80)) {
				value = 0xFFFFFFFF; // Negative, in two's complement form
			}
			value <<= 8;
			value |= b;
		}

		return value;
	}

	//TODO Check the OPAQUE UINT64 format
	private static long doReadLong(ByteBuffer buffer, int length) throws IOException {
		int value = 0;
		for (int i = 0; i < length; i++) {
			int b = buffer.get() & 0xFF;
			if ((i == 0) && ((b & 0x80) == 0x80)) {
				value = 0xFFFFFFFF; // Negative, in two's complement form
			}
			value <<= 8;
			value |= b;
		}

		return value;
	}

	private static float doReadFloat(ByteBuffer buffer, int length) throws IOException {
		return buffer.getFloat();
	}

	//TODO Check the OPAQUE DOUBLE format
	private static double doReadDouble(ByteBuffer buffer, int length) throws IOException {
		return buffer.getDouble();
	}

	private static BigInteger doReadUnsignedLongInteger(ByteBuffer buffer, int length) throws IOException {
		byte[] b = new byte[length];
		buffer.get(b);

		return new BigInteger(b);

		/*
		long value = 0;
		for (int i = 0; i < length; i++) {
			long b = buffer.get() & 0xFFL;
			value <<= 8;
			value |= b;
		}
		return value;
		*/
	}

	public int readInteger() throws IOException {
		int type = doReadType(buffer);
		if (type != BerConstants.INTEGER) {
			throw new IOException("Wrong ASN.1 type. Not an integer: " + type);
		}
		int length = doReadLength(buffer);
		return doReadInteger(buffer, length);
	}

	private static int[] doReadOid(ByteBuffer buffer, int length) throws IOException {
		List<Integer> values = new LinkedList<Integer>();

		if (length == 0) {
			throw new IOException("Invalid OID");
		}

		int b = buffer.get() & 0xFF;

		values.add(b / 40);
		values.add(b % 40);

		length--;

		int value = 0;
		while (length > 0) {
			b = buffer.get() & 0xFF;

			value <<= 7;
			value |= (b & ~0x80);

			if ((b & 0x80) == 0) {
				values.add(value);
				value = 0;
			}

			length--;
		}

		int[] v = new int[values.size()];
		int i = 0;
		for (int val : values) {
			v[i] = val;
			i++;
		}
		return v;
	}

	public Oid readOid() throws IOException {
		int type = doReadType(buffer);
		if (type != BerConstants.OID) {
			throw new IOException("Wrong type. Not an OID: " + type);
		}

		int length = doReadLength(buffer);
		return new Oid(doReadOid(buffer, length));
	}
	
	private static ByteBuffer doReadString(ByteBuffer buffer, int length) throws IOException {
		ByteBuffer b = ByteBuffer.wrap(buffer.array(), buffer.position(), length);
		buffer.position(buffer.position() + length);
		return b;
	}
	public ByteBuffer readBytes() throws IOException {
		int type = doReadType(buffer);
		if (type != BerConstants.OCTETSTRING) {
			throw new IOException("Wrong ASN.1 type. Not a string: " + type);
		}

		int length = doReadLength(buffer);
		return doReadString(buffer, length);
	}

	public void readNull() throws IOException {
		int type = doReadType(buffer);
		if (type != BerConstants.NULL) {
			throw new IOException("Wrong ASN.1 type. Not a null: " + type);
		}
		doReadLength(buffer);
	}

	public int beginReadSequence() throws IOException {
		int type = buffer.get() & 0xFF;
		if ((type != BerConstants.SEQUENCE) && (type != BerConstants.RESPONSE) && (type != BerConstants.REPORT) && (type != BerConstants.GET) && (type != BerConstants.GETNEXT) && (type != BerConstants.GETBULK))  {
			throw new IOException("Wrong ASN.1 type. Not a sequence: " + type);
		}
		int length = doReadLength(buffer);
		toReadLengthPositions.addFirst(buffer.position() + length);
		return type;
	}

	public void endReadSequence() throws IOException {
		int position = toReadLengthPositions.removeFirst();
		if (position != buffer.position()) {
			throw new IOException("Bad sequence: " + position + "!=" + buffer.position());
		}
	}

	public boolean hasRemainingInSequence() {
		int position = toReadLengthPositions.getFirst();
		return (buffer.position() < position);
	}

	public String readValue() throws IOException {
		return doReadValue(buffer, false);
	}

	private static String doReadValue(ByteBuffer buffer, boolean opaque) throws IOException {
	// public OidValue readOidValue() throws IOException {
		int type = buffer.get() & 0xFF;

		if ((type & BerConstants.ASN_BIT8) == BerConstants.ASN_BIT8) {
			if (!opaque) {
				int l = doReadLength(buffer);
				doReadString(buffer, l);
				return null;
			}
			type = buffer.get() & 0xFF; // OPAQUE wrapped type
		} else if (opaque) {
			int l = doReadLength(buffer);
			doReadString(buffer, l);
			return null;
		}

		int length = doReadLength(buffer);

		if (type == BerConstants.INTEGER) {
			return String.valueOf(doReadInteger(buffer, length));
			// return new OidValue(OidValue.Type.NUMBER, doReadInteger(length));
		}

		if (type == BerConstants.TIMETICKS) {
			return String.valueOf(doReadUnsignedLongInteger(buffer, length));
			// return new OidValue(OidValue.Type.TIME, doReadUnsignedLongInteger(length));
		}

		if ((type == BerConstants.COUNTER32) || (type == BerConstants.GAUGE32) || (type == BerConstants.COUNTER64) || (type == BerConstants.UNSIGNEDINTEGER32)) {
			return String.valueOf(doReadUnsignedLongInteger(buffer, length));
			// return new OidValue(OidValue.Type.NUMBER, doReadUnsignedLongInteger(length));
		}

		if (type == BerConstants.NULL) {
			if (length != 0) {
				throw new IOException("Invalid Null encoding, length is not zero");
			}
			return null;
			// return new OidValue(OidValue.Type.NULL);
		}

		if (type == BerConstants.OID) {
			return new Oid(doReadOid(buffer, length)).toString();
			// return new OidValue(OidValue.Type.OID, doReadOid(length));
		}

		if (type == BerConstants.IPADDRESS) {
			return asIpString(doReadString(buffer, length));
			// return new OidValue(OidValue.Type.IPADDRESS, doReadString(length));
		}

		if (type == BerConstants.OCTETSTRING) {
			return string(doReadString(buffer, length));
			// return new OidValue(OidValue.Type.STRING, doReadString(length));
		}

		if (type == BerConstants.OPAQUE) {
			ByteBuffer wrapped = doReadString(buffer, length);
			return doReadValue(wrapped, true);
			//%% return new OidValue(OidValue.Type.STRING, doReadString(length));
		}

		if (type == BerConstants.OPAQUE_FLOAT) {
			return String.valueOf(doReadFloat(buffer, length));
		}
		if (type == BerConstants.OPAQUE_DOUBLE) {
			return String.valueOf(doReadDouble(buffer, length));
		}
		if (type == BerConstants.OPAQUE_INTEGER64) {
			return String.valueOf(doReadLong(buffer, length));
		}
		if (type == BerConstants.OPAQUE_UNSIGNEDINTEGER64) {
			return String.valueOf(doReadUnsignedLongInteger(buffer, length));
		}

		// if ((type == BerConstants.ASN_BITSTRING) || (type ==
		// BerConstants.OPAQUE) || (type == BerConstants.NSAPADDRESS)) {
		return string(doReadString(buffer, length));
		// return new OidValue(OidValue.Type.OTHER, doReadString(length));
		// }
	}

	private static String asIpString(ByteBuffer bb) {
		ByteBuffer bytes = bb.duplicate();
		if (bytes.remaining() == 4) {
			StringBuilder b = new StringBuilder();
			while (bytes.hasRemaining()) {
				int k = bytes.get() & 0xFF;
				if (b.length() > 0) {
					b.append('.');
				}
				b.append(String.valueOf(k));
			}
			return b.toString();
		}

		StringBuilder b = new StringBuilder();
		while (bytes.hasRemaining()) {
			int k = ((bytes.get() & 0xFF) << 8) | (bytes.get() & 0xFF);
			String s = Integer.toHexString(k);
			if (b.length() > 0) {
				b.append(':');
			}
			b.append(s);
		}
		return b.toString();
	}
	
	private static String string(ByteBuffer bb) {
		ByteBuffer bytes = bb.duplicate();
		while (bytes.hasRemaining()) {
			int c = bytes.get() & 0xFF;
			if (((c < 32) && (c != 10) && (c != 13)) || (c >= 127)) {
				return BaseEncoding.base16().encode(bb.array(), bb.position(), bb.remaining());
			}
		}
		return BerPacketUtils.string(bb);
	}
}
