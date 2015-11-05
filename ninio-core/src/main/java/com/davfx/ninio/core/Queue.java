package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.ClassThreadFactory;
import com.davfx.ninio.util.Wait;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class Queue implements AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(Queue.class);

	private static final Config CONFIG = ConfigFactory.load();
	private static final int BUFFER_SIZE = CONFIG.getBytes("ninio.queue.buffer.size").intValue();
	
	private final long threadId;
	private final Selector selector;
	private final ConcurrentLinkedQueue<Runnable> toRun = new ConcurrentLinkedQueue<Runnable>(); // Using LinkedBlockingQueue my prevent OutOfMemory errors but may DEADLOCK
	
	public static Selector selector() {
		try {
			return SelectorProvider.provider().openSelector();
		} catch (IOException ioe) {
			LOGGER.error("Could not create selector", ioe);
			return null;
		}
	}
	
	public Queue() {
		this(selector());
	}
	private Queue(final Selector selector) {
		this.selector = selector;

		Thread t = new ClassThreadFactory(Queue.class).newThread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						if (selector == null) {
							throw new IOException("Selector could not be created");
						}
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
	public Wait finish() {
		Wait wait = new Wait();
		post(wait);
		return wait;
	}
	
	public void post(Runnable r) {
		if (isInside()) {
			r.run();
			return;
		}
		
		if (selector == null) {
			return;
		}
		toRun.add(r);
		selector.wakeup();
	}
	
	public ByteBufferAllocator allocator() {
		return new ByteBufferAllocator() {
			@Override
			public ByteBuffer allocate() {
				return ByteBuffer.allocate(BUFFER_SIZE);
			}
		};
	}
	
	@Override
	public void close() {
		if (selector == null) {
			return;
		}
		try {
			selector.close();
		} catch (IOException e) {
		}
		selector.wakeup();
	}
}
