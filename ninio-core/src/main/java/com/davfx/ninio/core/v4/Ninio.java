package com.davfx.ninio.core.v4;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.dependencies.Dependencies;
import com.davfx.ninio.util.ClassThreadFactory;
import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;

public final class Ninio { // implements AutoCloseable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Ninio.class);

	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(com.davfx.ninio.core.Ninio.class.getPackage().getName());
	private static final int THREADING = CONFIG.getInt("threading");
	private static final double WAIT_ON_ERROR = ConfigUtils.getDuration(CONFIG, "queue.waitOnError");

	private static final class Queues {
		private final NinioPriority priority;
		private final Object lock;
		// private boolean closed = false;
		private Selector selector = null;
		private ConcurrentLinkedQueue<Runnable> toRun = null;
		private int references = 0;

		private static final class InnerQueue implements Queue {
			private final Selector currentSelector;
			private final ConcurrentLinkedQueue<Runnable> currentToRun;
			private final Runnable finalization;
			public InnerQueue(Selector currentSelector, ConcurrentLinkedQueue<Runnable> currentToRun, Runnable finalization) {
				this.currentSelector = currentSelector;
				this.currentToRun = currentToRun;
				this.finalization = finalization;
			}
			@Override
			public SelectionKey register(SelectableChannel channel) throws ClosedChannelException {
				return channel.register(currentSelector, 0);
			}
			@Override
			public void execute(Runnable command) {
				currentToRun.add(command);
				currentSelector.wakeup();
			}
			@Override
			public void close() {
				finalization.run();
			}
		}
		
		public Queues(Object lock, NinioPriority priority) {
			this.lock = lock;
			this.priority = priority;
		}
		
		/*
		public void close() {
			Selector currentSelector;
			synchronized (lock) {
				if (closed) {
					return;
				}
				closed = true;
				currentSelector = selector;
			}
			if (currentSelector != null) {
				try {
					currentSelector.close();
				} catch (IOException e) {
				}
				currentSelector.wakeup();
			}
		}
		*/
		
		public Queue open() {
			Selector currentSelector;
			ConcurrentLinkedQueue<Runnable> currentToRun;

			Runnable finalization = new Runnable() {
				private boolean called = false;
				@Override
				public void run() {
					synchronized (lock) {
						if (called) {
							return;
						}
						/*
						if (closed) {
							return;
						}
						*/
						called = true;
						references--;
						if (references == 0) {
							LOGGER.debug("Queue not used anymore, closing the related thread");
							try {
								selector.close();
							} catch (IOException e) {
							}
							selector = null;
							toRun = null;
							references = 0;
						}
					}
				}
			};
			
			synchronized (lock) {
				/*
				if (closed) {
					return new Queue() {
						@Override
						public SelectionKey register(SelectableChannel channel) throws ClosedChannelException {
							throw new ClosedChannelException();
						}
						@Override
						public void execute(Runnable command) {
						}
						@Override
						public void close() {
						}
					};
				}
				*/
	
				if (selector != null) {
					references++;
					return new InnerQueue(selector, toRun, finalization);
				}
	
				LOGGER.debug("Starting a new queue thread");
	
				toRun = null;
				references = 0;
	
				try {
					selector = SelectorProvider.provider().openSelector();
				} catch (IOException ioe) {
					return null;
				}
				toRun = new ConcurrentLinkedQueue<Runnable>();
				references++;

				currentSelector = selector;
				currentToRun = toRun;
			}

			Thread t = new ClassThreadFactory(Queues.class).newThread(new Runnable() {
				@Override
				public void run() {
					while (true) {
						try {
							try {
								currentSelector.select();
							} catch (ClosedSelectorException ce) {
								return;
							}
							Set<SelectionKey> s;
							try {
								s = currentSelector.selectedKeys();
							} catch (ClosedSelectorException ce) {
								return;
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
						} catch (Throwable e) {
							LOGGER.error("Error in selector ", e);
							try {
								Thread.sleep((long) (WAIT_ON_ERROR * 1000d));
							} catch (InterruptedException ie) {
							}
						}

						while (!currentToRun.isEmpty()) {
							Runnable r = currentToRun.poll();
							try {
								r.run();
							} catch (Throwable e) {
								LOGGER.error("Error in running task", e);
							}
						}
					}
				}
			});
			
			if (priority == NinioPriority.HIGH) {
				t.setPriority(Thread.MAX_PRIORITY);
			}
			t.setDaemon(true);
			t.start();

			return new InnerQueue(currentSelector, currentToRun, finalization);
		}
	}

	private final Object lock = new Object();
	private final Queues[][] internalQueues;
	private int index = 0;

	public Ninio() {
		NinioPriority[] priorities = NinioPriority.values();
		internalQueues = new Queues[priorities.length][];
		for (int i = 0; i < internalQueues.length; i++) {
			NinioPriority priority = priorities[i];
			internalQueues[i] = new Queues[THREADING];
			for (int j = 0; j < internalQueues[i].length; j++) {
				internalQueues[i][j] = new Queues(lock, priority);
			}
		}
	}
	
	/*
	@Override
	public void close() {
		for (Queues[] q : internalQueues) {
			for (Queues internalQueue : q) {
				internalQueue.close();
			}
		}
	}
	*/
	
	public Queue register(NinioPriority priority) {
		int i;
		synchronized (lock) {
			i = index;
			index++;
			if (index == THREADING) {
				index = 0;
			}
		}
		return internalQueues[priority.ordinal()][i].open();
	}
}
