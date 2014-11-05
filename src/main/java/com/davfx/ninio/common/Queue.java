package com.davfx.ninio.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class Queue implements AutoCloseable, ByteBufferAllocator {
	private static final Logger LOGGER = LoggerFactory.getLogger(Queue.class);

	private static final Config CONFIG = ConfigUtils.load(Queue.class);
	private static final int BUFFER_SIZE = CONFIG.getBytes("ninio.queue.buffer.size").intValue();
	
	private final long threadId;
	private final Selector selector;
	private final ConcurrentLinkedQueue<Runnable> toRun = new ConcurrentLinkedQueue<Runnable>(); // Using LinkedBlockingQueue my prevent OutOfMemory errors but may DEADLOCK
	
	private final ByteBuffer buffer = ByteBuffer.wrap(new byte[BUFFER_SIZE]); // In the whole package, ByteBuffer is ALWAYS assumed to implement array(), so to be sure we wrap() instead of allocate()

	public static Selector selector() throws IOException {
		return SelectorProvider.provider().openSelector();
	}
	
	public Queue() throws IOException {
		this(selector());
	}
	public Queue(final Selector selector) {
		this.selector = selector;

		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						if (!selector.isOpen()) {
							break;
						}
						selector.select();
						if (!selector.isOpen()) {
							break;
						}
						Set<SelectionKey> s = selector.selectedKeys();
						for (SelectionKey key : s) {
							try {
								((SelectionKeyVisitor) key.attachment()).visit(key);
							} catch (Throwable e) {
								LOGGER.error("Error in event handling", e);
							}
						}
						s.clear();
					} catch (IOException e) {
						LOGGER.error("Error in queue", e);
						break;
					}
					
					while (!toRun.isEmpty()) {
						Runnable r = toRun.poll();
						try {
							r.run();
						} catch (Throwable e) {
							LOGGER.error("Error in running task", e);
						}
					}
				}
			}
		});
		
		t.setName("Queue");
		t.start();
		
		threadId = t.getId();
	}
	
	private boolean isInside() {
		return Thread.currentThread().getId() == threadId;
	}
	
	public Selector getSelector() {
		return selector;
	}

	public void check() {
		if (!isInside()) {
			throw new IllegalStateException("Should be in queue thread");
		}
	}
	
	public void post(Runnable r) {
		if (isInside()) {
			r.run();
			return;
		}
		
		toRun.add(r);
		selector.wakeup();
	}
	
	@Override
	public ByteBuffer allocate() {
		return buffer;
	}
	
	@Override
	public void close() {
		try {
			selector.close();
		} catch (IOException e) {
		}
		selector.wakeup();
	}
}
