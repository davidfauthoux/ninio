package com.davfx.ninio.core.v4;

public final class MutableByteArrays {
	private MutableByteArrays() {
	}

	public static MutableByteArray empty() {
		return new MutableByteArray(new byte[][] {});
	}
	
	public static long totalLength(MutableByteArray byteArray) {
		long l = 0L;
		for (byte[] b : byteArray.bytes) {
			l += b.length;
		}
		return l;
	}

	public static boolean isEmpty(MutableByteArray byteArray) {
		return (byteArray.bytes.length == 0);
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
