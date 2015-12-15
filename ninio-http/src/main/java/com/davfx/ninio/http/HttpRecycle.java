package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.util.QueueScheduled;
import com.davfx.util.ConfigUtils;
import com.davfx.util.DateUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

final class HttpRecycle implements AutoCloseable, Closeable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpRecycle.Recycler.class);
	
	private static final Config CONFIG = ConfigFactory.load(HttpRecycle.class.getClassLoader());

	private static final double RECYCLERS_TIME_TO_LIVE = ConfigUtils.getDuration(CONFIG, "ninio.http.recyclers.ttl");
	private static final double RECYCLERS_CHECK_TIME = ConfigUtils.getDuration(CONFIG, "ninio.http.recyclers.check");

	private static final class Recycler {
		public HttpResponseReader reader;
		public HttpClientHandler handler;
		public CloseableByteBufferHandler write;
		public double closeDate = 0d;
		public boolean closed = false;
	}

	private final ReadyFactory readyFactory;
	private final ReadyFactory secureReadyFactory;

	private final Map<Address, Deque<Recycler>> recyclers = new HashMap<Address, Deque<Recycler>>();
	private final Closeable closeable;

	public HttpRecycle(Queue queue, ReadyFactory readyFactory, ReadyFactory secureReadyFactory) {
		this.readyFactory = readyFactory;
		this.secureReadyFactory = secureReadyFactory;
		
		closeable = QueueScheduled.schedule(queue, RECYCLERS_CHECK_TIME, new Runnable() {
			@Override
			public void run() {
				double now = DateUtils.now();
				Iterator<Deque<Recycler>> recyclersIterator = recyclers.values().iterator();
				while (recyclersIterator.hasNext()) {
					Deque<Recycler> l = recyclersIterator.next();
					Iterator<Recycler> i = l.iterator();
					while (i.hasNext()) {
						Recycler r = i.next();
						if ((r.closeDate > 0d) && (r.closeDate <= now)) {
							r.write.close();
							i.remove();
						}
					}
					if (l.isEmpty()) {
						recyclersIterator.remove();
					}
				}
			}
		});
	}
	
	@Override
	public void close() {
		closeable.close();
		for (Deque<Recycler> l : recyclers.values()) {
			for (Recycler r : l) {
				r.write.close();
			}
		}
	}
	
	public void connect(final HttpRequest request, HttpClientHandler handler) {
		HttpResponseReader reader = new HttpResponseReader(handler);

		Deque<Recycler> oldRecyclers = recyclers.get(request.address);

		if (oldRecyclers != null) {
			while (!oldRecyclers.isEmpty()) {
				LOGGER.trace("Recycling connection to {}", request.address);
				final Recycler oldRecycler = oldRecyclers.removeFirst();
				if (oldRecyclers.isEmpty()) {
					recyclers.remove(request.address);
				}
				if (!oldRecycler.closed) {
					oldRecycler.reader = reader;
					oldRecycler.handler = handler;
					oldRecycler.closeDate = 0d;
					oldRecycler.write.handle(null, request.toByteBuffer());
					oldRecycler.handler.ready(new CloseableByteBufferHandler() {
						@Override
						public void close() {
						}
						
						@Override
						public void handle(Address address, ByteBuffer buffer) {
							oldRecycler.write.handle(address, buffer);							
						}
					});
					return;
				}
			}
		}

		final Recycler newRecycler = new Recycler();
		newRecycler.reader = reader;
		newRecycler.handler = handler;
		newRecycler.closeDate = 0d;
		Ready ready;
		if (request.secure) {
			ready = secureReadyFactory.create();
		} else {
			ready = readyFactory.create();
		}
		ready.connect(request.address, new ReadyConnection() {
			private HttpResponseReader.RecyclingHandler recyclingHandler;
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				if (newRecycler.reader == null) {
					return;
				}
				
				newRecycler.reader.handle(buffer, recyclingHandler);
			}
			
			@Override
			public void failed(IOException e) {
				newRecycler.closed = true;
				if (newRecycler.reader == null) {
					return;
				}
				newRecycler.reader.failed(e);
			}
			
			@Override
			public void connected(final FailableCloseableByteBufferHandler write) {
				if (newRecycler.handler == null) {
					return;
				}
				
				recyclingHandler = new HttpResponseReader.RecyclingHandler() {
					@Override
					public void recycle() {
						if (RECYCLERS_TIME_TO_LIVE == 0d) {
							close();
							return;
						}
						
						newRecycler.reader = null;
						newRecycler.handler = null;
						newRecycler.closeDate = DateUtils.now() + RECYCLERS_TIME_TO_LIVE;
						Deque<Recycler> oldRecyclers = recyclers.get(request.address);
						if (oldRecyclers == null) {
							oldRecyclers = new LinkedList<Recycler>();
							recyclers.put(request.address, oldRecyclers);
						}
						oldRecyclers.add(newRecycler);
					}
					@Override
					public void close() {
						write.close();
					}
				};

				newRecycler.write = new CloseableByteBufferHandler() {
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						write.handle(address, buffer);
					}
					@Override
					public void close() {
						write.close();
						newRecycler.closed = true;
						if (newRecycler.reader == null) {
							return;
						}
						newRecycler.reader.close();
					}
				};
				write.handle(null, request.toByteBuffer());
				newRecycler.handler.ready(new CloseableByteBufferHandler() {
					@Override
					public void close() {
					}
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						newRecycler.write.handle(address, buffer);
					}
				});
			}
			
			@Override
			public void close() {
				newRecycler.closed = true;
				if (newRecycler.reader == null) {
					return;
				}
				newRecycler.reader.close();
			}
		});
	}
}
