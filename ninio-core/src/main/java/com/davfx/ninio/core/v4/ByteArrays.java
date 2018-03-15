package com.davfx.ninio.core.v4;

import java.util.List;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.io.BaseEncoding;

public final class ByteArrays {
	private static final char REPRESENTATION_SEPARATOR = '\n';

	private ByteArrays() {
	}

	public static ByteArray cat(ByteArray... byteArrays) {
		int s = 0;
		for (ByteArray b : byteArrays) {
			s += b.bytes.length;
		}
		byte[][] bytes = new byte[s][];
		int k = 0;
		for (int i = 0; i < byteArrays.length; i++) {
			int l = byteArrays[i].bytes.length;
			System.arraycopy(byteArrays[i].bytes, 0, bytes, k, l);
			k += l;
		}
		return new ByteArray(bytes);
	}

	public static long totalLength(ByteArray byteArray) {
		long l = 0L;
		for (byte[] b : byteArray.bytes) {
			l += b.length;
		}
		return l;
	}

	public static byte[] flattened(ByteArray byteArray) {
		if (byteArray.bytes.length == 0) {
			return new byte[] {};
		}
		if (byteArray.bytes.length == 1) {
			return byteArray.bytes[0];
		}
		long s = 0L;
		for (byte[] b : byteArray.bytes) {
			s += b.length;
		}
		byte[] bb = new byte[(int) s];
		int i = 0;
		for (byte[] b : byteArray.bytes) {
			System.arraycopy(b, 0, bb, i, b.length);
			i += b.length;
		}
		return bb;
	}

	public static String representation(ByteArray byteArray) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < byteArray.bytes.length; i++) {
			if (i > 0) {
				b.append(REPRESENTATION_SEPARATOR);
			}
			b.append(BaseEncoding.base64().encode(byteArray.bytes[i]));
		}
		return b.toString();
	}

	public static ByteArray fromRepresentation(String representationAsBase64) {
		List<String> l = Splitter.on(REPRESENTATION_SEPARATOR).splitToList(representationAsBase64);
		byte[][] bytes = new byte[l.size()][];
		int i = 0;
		for (String s : l) {
			bytes[i] = BaseEncoding.base64().decode(s);
			i++;
		}
		return new ByteArray(bytes);
	}

	public static ByteArray utf8(String s) {
		return new ByteArray(new byte[][] { s.getBytes(Charsets.UTF_8) });
	}

	public static String utf8(ByteArray byteArray) {
		return new String(flattened(byteArray), Charsets.UTF_8);
	}
	
	public static MutableByteArray allocate(long length) {
		long m = Integer.MAX_VALUE;
		byte[][] bytes = new byte[((int) (length / m)) + ((length % m) > 0 ? 1 : 0)][];
		long l = length;
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = new byte[(int) Math.min(l, m)];
			l -= bytes[i].length;
		}
		return new MutableByteArray(bytes);
	}
}
