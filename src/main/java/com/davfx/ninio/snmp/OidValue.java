package com.davfx.ninio.snmp;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.text.MessageFormat;

public final class OidValue {

	public static enum Type {
		STRING,
		NUMBER,
		NULL,
		OID,
		IPADDRESS,
		TIME,
		OTHER,
	}

	private final OidValue.Type type;
	private final Integer intValue;
	// private final Long longValue;
	private final BigInteger bigIntegerValue;
	private final int[] intArrayValue;
	private final ByteBuffer byteArrayValue;
	//%%% private final String stringValue;

	OidValue(OidValue.Type type) {
		this.type = type;
		intValue = null;
		// longValue = null;
		bigIntegerValue = null;
		intArrayValue = null;
		byteArrayValue = null;
		//%% stringValue = null;
	}
	OidValue(OidValue.Type type, int value) {
		this.type = type;
		intValue = value;
		// longValue = null;
		bigIntegerValue = null;
		intArrayValue = null;
		byteArrayValue = null;
		//%% stringValue = null;
	}
	/*
	OidValue(OidValue.Type type, long value) {
		this.type = type;
		intValue = null;
		longValue = value;
		bigIntegerValue = null;
		intArrayValue = null;
		byteArrayValue = null;
		stringValue = null;
	}
	*/
	OidValue(OidValue.Type type, BigInteger value) {
		this.type = type;
		intValue = null;
		// longValue = null;
		bigIntegerValue = value;
		intArrayValue = null;
		byteArrayValue = null;
		//%% stringValue = null;
	}
	OidValue(OidValue.Type type, int[] value) {
		this.type = type;
		intValue = null;
		// longValue = null;
		bigIntegerValue = null;
		intArrayValue = value;
		byteArrayValue = null;
		//%% stringValue = null;
	}
	OidValue(OidValue.Type type, ByteBuffer value) {
		this.type = type;
		intValue = null;
		// longValue = null;
		bigIntegerValue = null;
		intArrayValue = null;
		byteArrayValue = value;
		//%% stringValue = null;
	}
	/*%%%
	OidValue(OidValue.Type type, String value) {
		this.type = type;
		intValue = null;
		// longValue = null;
		bigIntegerValue = null;
		intArrayValue = null;
		byteArrayValue = null;
		stringValue = value;
	}
	*/

	public OidValue.Type getType() {
		return type;
	}
	public int asInt() {
		if (intValue != null) {
			return intValue;
		}
		/*
		if (longValue != null) {
			return longValue;
		}
		 */
		if (bigIntegerValue != null) {
			return (int) bigIntegerValue.longValue();
		}
		return 0;
	}
	public long asLong() {
		/*
		if (longValue != null) {
			return longValue;
		}
		*/
		if (bigIntegerValue != null) {
			return bigIntegerValue.longValue();
		}
		if (intValue != null) {
			return intValue;
		}
		return 0L;
	}
	public BigInteger asBigInteger() {
		if (bigIntegerValue != null) {
			return bigIntegerValue;
		}
		/*
		if (longValue != null) {
			return BigInteger.valueOf(longValue);
		}
		*/
		if (intValue != null) {
			return BigInteger.valueOf(intValue);
		}
		return BigInteger.ZERO;
	}
	public int[] asIntArray() {
		if (intArrayValue != null) {
			return intArrayValue;
		}
		if (byteArrayValue != null) {
			ByteBuffer b = byteArrayValue.duplicate();
			int[] r = new int[b.remaining()];
			int i = 0;
			while (b.hasRemaining()) {
				r[i] = b.get() & 0xFF;
				i++;
			}
			return r;
		}
		return null;
	}
	public ByteBuffer asByteArray() {
		if (byteArrayValue != null) {
			return byteArrayValue.duplicate();
		}
		if (intArrayValue != null) {
			byte[] r = new byte[intArrayValue.length];
			int i = 0;
			for (int b : intArrayValue) {
				r[i] = (byte) (b & 0xFF);
				i++;
			}
			return ByteBuffer.wrap(r);
		}
		return null;
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
			if ((c < 32) || (c >= 127)) {
				String s = HexUtils.toHexString(bb);
				return s;
			}
		}
		return BerPacketUtils.string(bb);
	}

	public String asString() {
		//%% if (stringValue != null) {
		//%% return stringValue;
		//%% }
		if (byteArrayValue != null) {
			if (type == OidValue.Type.IPADDRESS) {
				return asIpString(byteArrayValue);
			}
			return string(byteArrayValue);
		}
		if (intArrayValue != null) {
			if (type == OidValue.Type.OID) {
				return new Oid(intArrayValue).toString();
			}
			return HexUtils.toHexString(intArrayValue);
		}
		if (type == OidValue.Type.TIME) {
			long tt = asLong();

			long days = tt / 8640000L;
			tt %= 8640000L;

			long hours = tt / 360000L;
			tt %= 360000L;

			long minutes = tt / 6000L;
			tt %= 6000L;

			long seconds = tt / 100L;
			tt %= 100L;

			long hseconds = tt;

			Long[] values = new Long[5];
			values[0] = days;
			values[1] = hours;
			values[2] = minutes;
			values[3] = seconds;
			values[4] = hseconds;

			return MessageFormat.format("{0,choice,0#|1#1 day, |1<{0,number,integer} days, }{1,number,integer}:{2,number,00}:{3,number,00}.{4,number,00}", (Object[]) values);
		}
		if (bigIntegerValue != null) {
			return bigIntegerValue.toString();
		}
		if (intValue != null) {
			return String.valueOf(intValue);
		}
		/*
		if (longValue != null) {
			return String.valueOf(longValue);
		}
		*/
		return String.valueOf((String) null);
	}

	@Override
	public String toString() {
		return type.toString() + ":" + asString();
	}
}
