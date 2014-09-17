package com.davfx.ninio.common;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Queue implements AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(Queue.class);

	private final String name = Queue.class.getCanonicalName();
	
	private final Selector selector;
	private final ConcurrentLinkedQueue<Runnable> toRun = new ConcurrentLinkedQueue<Runnable>();

	public static Selector selector() throws IOException {
		return SelectorProvider.provider().openSelector();
	}
	
	public Queue() throws IOException {
		this(selector());
	}
	public Queue(final Selector selector) {
		this.selector = selector;
		
		Threads.run(Queue.class, new Runnable() {
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
	}
	
	private boolean isInside() {
		return Thread.currentThread().getName().equals(name);
	}
	
	public Selector getSelector() {
		return selector;
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
	public void close() {
		try {
			selector.close();
		} catch (IOException e) {
		}
		selector.wakeup();
	}
}
