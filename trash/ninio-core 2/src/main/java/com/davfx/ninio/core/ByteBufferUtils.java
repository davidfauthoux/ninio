package com.davfx.ninio.core;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import com.google.common.base.Charsets;

public final class ByteBufferUtils {
	private ByteBufferUtils() {
	}
	
	public static String toString(ByteBuffer b, Charset charset) {
		return new String(b.array(), b.arrayOffset() + b.position(), b.remaining(), charset);
	}
	public static String toString(ByteBuffer b) {
		return toString(b, Charsets.UTF_8);
	}
	
	public static ByteBuffer toByteBuffer(String s, Charset charset) {
		return ByteBuffer.wrap(s.getBytes(charset));
	}
	public static ByteBuffer toByteBuffer(String s) {
		return toByteBuffer(s, Charsets.UTF_8);
	}
}
