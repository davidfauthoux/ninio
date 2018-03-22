package com.davfx.ninio.core.v4;

import java.nio.ByteBuffer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public final class ByteArrayProducer {
	private static final Logger LOGGER = LoggerFactory.getLogger(ByteArrayProducer.class);

	private static final int MIN_BUFFER_SIZE = 100;
	
	private final List<ByteBuffer> bytes = Lists.newArrayList();
	private ByteBuffer current = null;

	public ByteArrayProducer() {
	}
	
	public ByteArray finish() {
		byte[][] bb = new byte[bytes.size()][];
		int i = 0;
		for (ByteBuffer b : bytes) {
			b.flip();
			if ((b.position() == 0) && (b.remaining() == b.array().length)) {
				bb[i] = b.array();
			} else {
				bb[i] = new byte[b.remaining()];
				b.get(bb[i]);
			}
			i++;
		}
		return new ByteArray(bb);
	}

	private ByteBuffer check(int size) {
		if (current != null) {
			if (current.remaining() < size) {
				current = null;
			}
		}
		if (current == null) {
			current = ByteBuffer.allocate(MIN_BUFFER_SIZE);
			bytes.add(current);
		}
		return current;
	}
	
	public ByteArrayProducer produceByte(byte value) {
		check(1).put(value);
		return this;
	}
	
	public ByteArrayProducer produceInt(int value) {
		check(Ints.BYTES).putInt(value);
		return this;
	}
	
	public ByteArrayProducer produceLong(long value) {	
		check(Longs.BYTES).putLong(value);
		return this;
	}
	
	public ByteArrayProducer produceBytes(byte[] value, int position, int length) {
		if ((position == 0) && (length == value.length)) {
			current = null;
			ByteBuffer b = ByteBuffer.wrap(value);
			b.position(length);
			bytes.add(b);
		} else {
			LOGGER.warn("Costly ByteArray production (position={}, length={})", position, length);
			check(length).put(value, position, length);
		}
		return this;
	}
	public ByteArrayProducer produceBytes(byte[] value) {
		produceBytes(value, 0, value.length);
		return this;
	}
}
