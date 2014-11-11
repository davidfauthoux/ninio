package com.davfx.ninio.http.util;

import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;

import com.davfx.ninio.http.Http;

public final class InMemoryPost implements Iterable<ByteBuffer> {
	private final Deque<ByteBuffer> buffers = new LinkedList<>();
	
	public InMemoryPost() {
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
	
	public int getBytes(byte[] destination, int off, int len) {
		int i = 0;
		ByteBuffer b = null;
		while (i < len) {
			if (b == null) {
				if (buffers.isEmpty()) {
					break;
				}
				b = buffers.getFirst();
			}
			if (b == null) {
				break;
			}
			int l = len - i;
			if (b.remaining() > l) {
				l = b.remaining();
			}
			b.get(destination, off, l);
			if (!b.hasRemaining()) {
				buffers.removeFirst();
				b = null;
			}
		}
		return i;
	}

	public int getByte() {
		if (buffers.isEmpty()) {
			return -1;
		}
		ByteBuffer b = buffers.getFirst();
		int r = b.get();
		if (!b.hasRemaining()) {
			buffers.removeFirst();
		}
		return r;
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
	
	@Override
	public String toString() {
		byte[] b = toByteArray();
		return new String(b, 0, b.length, Http.UTF8_CHARSET);
	}
}
