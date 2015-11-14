package com.davfx.ninio.http.util;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.CloseableByteBufferHandler;

@Deprecated
final class ByteBufferHandlerInputStream extends InputStream implements CloseableByteBufferHandler {
	private final Deque<ByteBuffer> buffers = new LinkedList<>();
	private boolean closed = false;

	public ByteBufferHandlerInputStream() {
	}
	
	@Override
	public void handle(Address address, ByteBuffer buffer) {
		if (!buffer.hasRemaining()) {
			return;
		}
		synchronized (buffers) {
			buffers.addLast(buffer);
			buffers.notifyAll();
		}
	}
	
	@Override
	public int read(byte[] b, int off, int len) {
		int count = 0;
		synchronized (buffers) {
			while (buffers.isEmpty()) {
				if (closed) {
					break;
				}
				try {
					buffers.wait();
				} catch (InterruptedException e) {
				}
			}
			ByteBuffer bb = buffers.getFirst();
			int l = Math.min(bb.remaining(), len);
			bb.get(b, off, l);
			off += l;
			len -= l;
			count += l;
			if (!bb.hasRemaining()) {
				buffers.removeFirst();
			}
		}
		if (count == 0) {
			return -1;
		}
		return count;
	}
	
	@Override
	public int read(byte[] b) {
		return read(b, 0, b.length);
	}
	@Override
	public int read() {
		byte[] b = new byte[1];
		read(b);
		return b[0];
	}
	
	@Override
	public int available() {
		int n = 0;
		synchronized (buffers) {
			for (ByteBuffer b : buffers) {
				n += b.remaining();
			}
		}
		return n;
	}
	
	@Override
	public long skip(long n) {
		long count = 0L;
		synchronized (buffers) {
			while (buffers.isEmpty()) {
				if (closed) {
					break;
				}
				try {
					buffers.wait();
				} catch (InterruptedException e) {
				}
			}
			ByteBuffer bb = buffers.getFirst();
			long l = Math.min(bb.remaining(), n);
			n -= l;
			count += l;
			if (!bb.hasRemaining()) {
				buffers.removeFirst();
			}
		}
		if (count == 0L) {
			return -1L;
		}
		return count;
	}
	
	@Override
	public void close() {
		synchronized (buffers) {
			closed = true;
			buffers.notifyAll();
		}
	}
	
	@Override
	public boolean markSupported() {
		return false;
	}
	@Override
	public void mark(int readlimit) {
	}
	@Override
	public void reset() {
	}
}
