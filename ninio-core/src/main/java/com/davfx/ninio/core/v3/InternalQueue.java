package com.davfx.ninio.core.v3;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.util.ClassThreadFactory;

public final class InternalQueue {
	private static final Logger LOGGER = LoggerFactory.getLogger(InternalQueue.class);

	private static final double WAIT_ON_SELECTOR_ERROR = 0.5d;
	
	static final Selector SELECTOR;
	private static final ConcurrentLinkedQueue<Runnable> TO_RUN = new ConcurrentLinkedQueue<Runnable>(); // Using LinkedBlockingQueue my prevent OutOfMemory errors but may DEADLOCK
	public static Executor EXECUTOR;
	static {
		Selector s;
		try {
			s = SelectorProvider.provider().openSelector();
		} catch (IOException ioe) {
			LOGGER.error("Could not create selector", ioe);
			throw new RuntimeException(ioe);
		}
		SELECTOR = s;

		Thread t = new ClassThreadFactory(InternalQueue.class).newThread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						SELECTOR.select();

						if (SELECTOR.isOpen()) {
							Set<SelectionKey> s;
							try {
								s = SELECTOR.selectedKeys();
							} catch (ClosedSelectorException ce) {
								s = null;
							}
							if (s != null) {
								for (SelectionKey key : s) {
									try {
										((SelectionKeyVisitor) key.attachment()).visit(key);
									} catch (Throwable e) {
										LOGGER.error("Error in event handling", e);
									}
								}
								s.clear();
							}
						}
					} catch (Throwable e) {
						LOGGER.error("Error in selector ", e);
						try {
							Thread.sleep((long) (WAIT_ON_SELECTOR_ERROR * 1000d));
						} catch (InterruptedException ie) {
						}
					}

					while (!TO_RUN.isEmpty()) {
						Runnable r = TO_RUN.poll();
						try {
							r.run();
						} catch (Throwable e) {
							LOGGER.error("Error in running task", e);
						}
					}
				}
			}
		});
		t.setDaemon(true);
		t.start();
		
		EXECUTOR = new Executor() {
			@Override
			public void execute(Runnable command) {
				TO_RUN.add(command);
				SELECTOR.wakeup();
			}
		};
	}
}
