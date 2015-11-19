package com.davfx.ninio.http;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;

import com.google.common.base.Charsets;

public final class InMemoryBuffers implements Iterable<ByteBuffer> {
	private final Deque<ByteBuffer> buffers = new LinkedList<>();
	
	public InMemoryBuffers() {
	}
	
	@Override
	public Iterator<ByteBuffer> iterator() {
		return buffers.iterator();
	}
	
	public void add(ByteBuffer buffer) {
		if (!buffer.hasRemaining()) {
			return;
		}
		buffers.addLast(buffer);
	}
	
	public int getSize() {
		int l = 0;
		for (ByteBuffer b : buffers) {
			l += b.remaining();
		}
		return l;
	}
	
	public byte[] toByteArray() {
		byte[] b = new byte[getSize()];
		int off = 0;
		for (ByteBuffer bb : buffers) {
			int pos = bb.position();
			int r = bb.remaining();
			bb.get(b, off, bb.remaining());
			off += r;
			bb.position(pos);
		}
		return b;
	}
	
	public String toString(Charset charset) {
		byte[] b = toByteArray();
		return new String(b, 0, b.length, charset);
	}
	@Override
	public String toString() {
		return toString(Charsets.UTF_8);
	}
}
