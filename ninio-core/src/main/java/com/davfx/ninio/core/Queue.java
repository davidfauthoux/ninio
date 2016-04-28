package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.util.ClassThreadFactory;
import com.davfx.util.Wait;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class Queue implements AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(Queue.class);

	private static final Config CONFIG = ConfigFactory.load(Queue.class.getClassLoader());
	private static final int BUFFER_SIZE = CONFIG.getBytes("ninio.queue.buffer").intValue();
	
	private static final double WAIT_ON_SELECTOR_ERROR = 0.5d;
	
	private final String name;
	private final long threadId;
	private final Selector selector;
	private final ConcurrentLinkedQueue<Runnable> toRun = new ConcurrentLinkedQueue<Runnable>(); // Using LinkedBlockingQueue my prevent OutOfMemory errors but may DEADLOCK

	public Queue() {
		this(null);
	}
	public Queue(final String name) {
		this.name = name;
		
		LOGGER.debug("Queue created");
		
		Selector s;
		try {
			s = SelectorProvider.provider().openSelector();
		} catch (IOException ioe) {
			LOGGER.error("Could not create selector (name = {})", name, ioe);
			throw new RuntimeException(ioe);
		}
		selector = s;

		Thread t = new ClassThreadFactory(Queue.class, name).newThread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						if (!selector.isOpen()) {
							break;
						}
						selector.select();

						if (selector.isOpen()) {
							Set<SelectionKey> s;
							try {
								s = selector.selectedKeys();
							} catch (ClosedSelectorException ce) {
								s = null;
							}
							if (s != null) {
								for (SelectionKey key : s) {
									try {
										((SelectionKeyVisitor) key.attachment()).visit(key);
									} catch (Throwable e) {
										LOGGER.error("Error in event handling (name = {})", name, e);
									}
								}
								s.clear();
							}
						}
					} catch (Throwable e) {
						LOGGER.error("Error in selector (name = {})", name, e);
						try {
							Thread.sleep((long) (WAIT_ON_SELECTOR_ERROR * 1000d));
						} catch (InterruptedException ie) {
						}
					}
					
					while (!toRun.isEmpty()) {
						Runnable r = toRun.poll();
						try {
							r.run();
						} catch (Throwable e) {
							LOGGER.error("Error in running task (name = {})", name, e);
						}
					}
				}
			}
		});
		t.setDaemon(true);
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
			throw new IllegalStateException("Should be in queue thread (" + name + ")");
		}
	}
	public Wait finish() {
		LOGGER.trace("Finishing queue (name = {})", name);
		Wait wait = new Wait();
		post(wait);
		return wait;
	}
	
	public void post(Runnable r) {
		if (isInside()) {
			r.run();
			return;
		}

		toRun.add(r);
		if (!selector.isOpen()) {
			LOGGER.warn("Selector closed (name = {})", name);
			return;
		}
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
		if (isInside()) {
			throw new IllegalStateException("Should not be in queue thread (name = " + name + ")");
		}
		try {
			selector.close();
		} catch (IOException e) {
		}
		selector.wakeup();
	}
}
