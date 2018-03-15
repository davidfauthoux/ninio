package com.davfx.ninio.core.v4;

import java.nio.ByteBuffer;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public final class ByteArrayConsumer {
	private final ByteArray byteArray;
	private int next = 0;
	private ByteBuffer current = null;

	public ByteArrayConsumer(ByteArray byteArray) {
		this.byteArray = byteArray;
	}

	private ByteBuffer check(int size) {
		if (current == null) {
			if (next == byteArray.bytes.length) {
				throw new ArrayIndexOutOfBoundsException();
			}
			current = ByteBuffer.wrap(byteArray.bytes[next]);
			next++;
		}
		if (size < 0L) {
			if (current.position() > 0) {
				throw new IllegalArgumentException("Could not partially consume an internal byte array");
			}
		}
		if (current.remaining() < size) {
			throw new IllegalArgumentException("Could not consume over two internal byte arrays");
		}
		return current;
	}
	
	private void uncheck() {
		if (current == null) {
			return;
		}
		if (!current.hasRemaining()) {
			current = null;
		}
	}
	
	public byte consumeByte() {
		try {
			return check(1).get();
		} finally {
			uncheck();
		}
	}

	public int consumeInt() {
		try {
			return check(Ints.BYTES).getInt();
		} finally {
			uncheck();
		}
	}

	public long consumeLong() {
		try {
			return check(Longs.BYTES).getLong();
		} finally {
			uncheck();
		}
	}
	
	public byte[] consumeBytes() {
		try {
			ByteBuffer b = check(-1);
			b.position(b.position() + b.remaining());
			return b.array();
		} finally {
			uncheck();
		}
	}
	
	public interface Callback {
		void consume(byte[] b, int position, int length);
	}
	
	public void consume(Callback consumer, long length) {
		if (length < 0L) {
			throw new IllegalArgumentException();
		}
		while (true) {
			if (length == 0L) {
				break;
			}
			if (current == null) {
				if (next == byteArray.bytes.length) {
					throw new ArrayIndexOutOfBoundsException();
				}
				current = ByteBuffer.wrap(byteArray.bytes[next]);
				next++;
			}

			int l = (int) Math.min(length, current.remaining());
			consumer.consume(current.array(), current.position(), l);
			length -= l;
			current.position(current.position() + l);
			uncheck();
		}
	}
	
	public void consume(Callback consumer) {
		while (true) {
			if (current == null) {
				if (next == byteArray.bytes.length) {
					break;
				}
				current = ByteBuffer.wrap(byteArray.bytes[next]);
				next++;
			}

			consumer.consume(current.array(), current.position(), current.remaining());
			current.position(current.position() + current.remaining());
			uncheck();
		}
	}
}
