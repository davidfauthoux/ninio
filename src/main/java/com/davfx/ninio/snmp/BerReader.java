package com.davfx.ninio.snmp;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

public final class BerReader {
	private final ByteBuffer buffer;
	private final Deque<Integer> toReadLengthPositions = new LinkedList<Integer>();

	public BerReader(ByteBuffer buffer) {
		this.buffer = buffer;
	}

	private int readType() throws IOException {
		int b = buffer.get() & 0xFF;
		if ((b & BerConstants.ASN_BIT8) == BerConstants.ASN_BIT8) {
			throw new IOException("No such object");
		}
		return b;
	}

	// Strict reading

	// checked
	private int readLength() throws IOException {
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

	private int doReadInteger(int length) throws IOException {
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

	private BigInteger doReadUnsignedLongInteger(int length) throws IOException {
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
		int type = readType();
		if (type != BerConstants.INTEGER) {
			throw new IOException("Wrong ASN.1 type. Not an integer: " + type);
		}
		int length = readLength();
		return doReadInteger(length);
	}

	private int[] doReadOid(int length) throws IOException {
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
		int type = readType();
		if (type != BerConstants.OID) {
			throw new IOException("Wrong type. Not an OID: " + type);
		}

		int length = readLength();
		return new Oid(doReadOid(length));
	}
	
	private ByteBuffer doReadString(int length) throws IOException {
		ByteBuffer b = buffer.duplicate();
		b.limit(b.position() + length);
		buffer.position(buffer.position() + length);
		return b;
	}
	public ByteBuffer readBytes() throws IOException {
		int type = readType();
		if (type != BerConstants.OCTETSTRING) {
			throw new IOException("Wrong ASN.1 type. Not a string: " + type);
		}

		int length = readLength();
		return doReadString(length);
	}

	public void readNull() throws IOException {
		int type = readType();
		if (type != BerConstants.NULL) {
			throw new IOException("Wrong ASN.1 type. Not a null: " + type);
		}
		readLength();
	}

	public int beginReadSequence() throws IOException {
		int type = buffer.get() & 0xFF;
		if ((type != BerConstants.SEQUENCE) && (type != BerConstants.RESPONSE) && (type != BerConstants.REPORT) && (type != BerConstants.GET) && (type != BerConstants.GETNEXT) && (type != BerConstants.GETBULK))  {
			throw new IOException("Wrong ASN.1 type. Not a sequence: " + type);
		}
		int length = readLength();
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

	public OidValue readOidValue() throws IOException {
		int type = buffer.get() & 0xFF;
		int length = readLength();

		if ((type & BerConstants.ASN_BIT8) == BerConstants.ASN_BIT8) {
			return null;
		}

		if (type == BerConstants.INTEGER) {
			return new OidValue(OidValue.Type.NUMBER, doReadInteger(length));
		}

		if (type == BerConstants.TIMETICKS) {
			return new OidValue(OidValue.Type.TIME, doReadUnsignedLongInteger(length));
		}

		if ((type == BerConstants.COUNTER32) || (type == BerConstants.GAUGE32) || (type == BerConstants.COUNTER64) || (type == BerConstants.UNSIGNEDINTEGER32)) {
			return new OidValue(OidValue.Type.NUMBER, doReadUnsignedLongInteger(length));
		}

		if (type == BerConstants.NULL) {
			if (length != 0) {
				throw new IOException("Invalid Null encoding, length is not zero");
			}
			return new OidValue(OidValue.Type.NULL);
		}

		if (type == BerConstants.OID) {
			return new OidValue(OidValue.Type.OID, doReadOid(length));
		}

		if (type == BerConstants.IPADDRESS) {
			byte[] value = new byte[length];
			buffer.get(value);
			return new OidValue(OidValue.Type.IPADDRESS, doReadString(length));
		}

		if (type == BerConstants.OCTETSTRING) {
			return new OidValue(OidValue.Type.STRING, doReadString(length));
		}

		// if ((type == BerConstants.ASN_BITSTRING) || (type ==
		// BerConstants.OPAQUE) || (type == BerConstants.NSAPADDRESS)) {
		return new OidValue(OidValue.Type.OTHER, doReadString(length));
		// }
	}
}
