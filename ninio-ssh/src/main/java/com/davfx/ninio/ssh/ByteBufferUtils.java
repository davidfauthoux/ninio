package com.davfx.ninio.ssh;

import java.nio.ByteBuffer;

final class ByteBufferUtils {
	private ByteBufferUtils() {
	}
	
	public static void transfer(ByteBuffer b, ByteBuffer to) {
		int p = b.position();
		b.get(to.array(), to.position(), Math.min(b.remaining(), to.capacity() - to.position()));
		int l = b.position() - p;
		to.position(to.position() + l);
	}
}
