package com.davfx.ninio.http.v3.service;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.http.v3.HttpContentReceiver;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class ByteBufferHandlerInputStream extends InputStream {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ByteBufferHandlerInputStream.class);
	
	private static final Config CONFIG = ConfigFactory.load(ByteBufferHandlerInputStream.class.getClassLoader());
	private static final int POST_LIMIT = CONFIG.getBytes("ninio.http.service.post.limit").intValue();

	private final Deque<ByteBuffer> buffers = new LinkedList<>();
	private boolean end = false;
	private boolean closed = false;
	private int count = 0;
	private boolean overflow = false;
	public final HttpContentReceiver receiver;
	
	public ByteBufferHandlerInputStream() {
		receiver = new HttpContentReceiver() {
			@Override
			public void ended() {
				synchronized (buffers) {
					if (closed) {
						return;
					}
					if (overflow) {
						return;
					}
					buffers.addLast(null);
					buffers.notifyAll();
				}
			}

			@Override
			public void received(ByteBuffer buffer) {
				if (!buffer.hasRemaining()) {
					return;
				}
				synchronized (buffers) {
					if (closed) {
						return;
					}
					if (overflow) {
						return;
					}
					buffers.addLast(buffer);
					count += buffer.remaining();
					if (count >= POST_LIMIT) {
						LOGGER.warn("Post overflow (> {} bytes), it should be read faster", POST_LIMIT);
						buffers.clear();
						overflow = true;
					}
					buffers.notifyAll();
				}
			}
		};
	}
	
	public int waitFor() {
		int l = 0;
		synchronized (buffers) {
			while (true) {
				if (overflow) {
					return 0;
				}
				if (!buffers.isEmpty()) {
					if (buffers.getLast() == null) {
						break;
					}
				}
				try {
					buffers.wait();
				} catch (InterruptedException e) {
				}
			}
			for (ByteBuffer bb : buffers) {
				if (bb == null) {
					break;
				}
				l += bb.remaining();
			}
		}
		return l;
	}

	@Override
	public int read(byte[] b, int off, int len) {
		if (end) {
			return -1;
		}
		
		ByteBuffer bb;
		synchronized (buffers) {
			while (true) {
				if (overflow) {
					return -1;
				}
				if (!buffers.isEmpty()) {
					bb = buffers.removeFirst();
					if (bb != null) {
						if (bb.remaining() > len) {
							ByteBuffer back = bb.duplicate();
							back.position(back.position() + len);
							buffers.addFirst(back);
							bb.limit(bb.position() + len);
						}
						count -= bb.remaining();
					}
					break;
				}
				try {
					buffers.wait();
				} catch (InterruptedException e) {
				}
			}
		}
		
		if (bb == null) {
			end = true;
			return -1;
		}
		
		int l = Math.min(bb.remaining(), len);
		bb.get(b, off, l);
		return l;
	}
	@Override
	public int read(byte[] b) {
		return read(b, 0, b.length);
	}
	@Override
	public int read() {
		byte[] b = new byte[1];
		read(b);
		return b[0] & 0xFF;
	}
	
	@Override
	public int available() {
		if (end) {
			return 0;
		}
		
		int a = 0;
		synchronized (buffers) {
			for (ByteBuffer bb : buffers) {
				a += bb.remaining();
			}
		}
		
		return a;
	}
	
	@Override
	public void close() {
		synchronized (buffers) {
			closed = true;
			buffers.clear();
		}
	}
	
	@Override
	public long skip(long n) {
		if (end) {
			return 0L;
		}
		
		ByteBuffer bb;
		synchronized (buffers) {
			while (true) {
				if (overflow) {
					return -1;
				}
				if (!buffers.isEmpty()) {
					bb = buffers.removeFirst();
					if (bb != null) {
						if (bb.remaining() > n) {
							int nn = (int) n;
							ByteBuffer back = bb.duplicate();
							back.position(back.position() + nn);
							buffers.addFirst(back);
							bb.limit(bb.position() + nn);
						}
						count -= bb.remaining();
					}
					break;
				}
				try {
					buffers.wait();
				} catch (InterruptedException e) {
				}
			}
		}
		
		if (bb == null) {
			end = true;
			return 0L;
		}
		
		int l = (int) Math.min(bb.remaining(), n);
		bb.position(bb.position() + l);
		return l;
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
