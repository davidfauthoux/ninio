package com.davfx.ninio.snmp;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Count;
import com.davfx.ninio.core.CountingCurrentOpenReady;
import com.davfx.ninio.core.CountingCurrentOpenReadyFactory;
import com.davfx.ninio.core.DatagramReady;
import com.davfx.ninio.core.DatagramReadyFactory;
import com.davfx.ninio.core.Queue;
import com.davfx.util.Lock;

public class SnmpCacheTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpCacheTest.InnerCount.class);
	
	private static final class InnerCount implements Count {
		private final String prefix;
		public long count = 0L;
		public InnerCount(String prefix) {
			this.prefix = prefix;
		}
		@Override
		public void inc(long delta) {
			count += delta;
		}
		@Override
		public String toString() {
			return prefix + count;
		}
	}
	
	@Test
	public void test() throws Exception {
		InnerCount serverOpenCount = new InnerCount("server:");
		InnerCount openCount = new InnerCount("open:");
		
		double delayToDiscard = 1d;
		
		try (Queue queue = new Queue()) {
			final String[] diff = new String[] { null };
			try (SnmpServer snmpServer = new SnmpServer(queue, new CountingCurrentOpenReady(serverOpenCount, new DatagramReady(queue.getSelector(), queue.allocator()).bind()), new Address(Address.LOCALHOST, 8080), new SnmpServer.Handler() {
				@Override
				public void from(Oid oid, Callback callback) {
					if (oid.equals(new Oid("1.1.1"))) {
						for (int i = 1; i <= 3; i++) {
							callback.handle(oid.append(new Oid("" + i)), diff[0] + i);
						}
					} else if (new Oid("1.1.1").isPrefixOf(oid)) {
						callback.handle(oid, diff[0] + new Oid("1.1.1").sub(oid).getRaw()[0]);
					} else if (new Oid("1.1.2").isPrefixOf(oid)) {
						callback.handle(oid, "" + new Oid("1.1.2").sub(oid).getRaw()[0]);
					}
				}
			})) {
				queue.finish().waitFor();

				diff[0] = "A";
				Assertions.assertThat(test(openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:A1, 1.1.1.2:A2, 1.1.1.3:A3]");
				diff[0] = "AA";
				Assertions.assertThat(test(openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:AA2]");
				diff[0] = "B";
				Assertions.assertThat(test(openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:B1, 1.1.1.2:B2, 1.1.1.3:B3]");
				diff[0] = "BB";
				Assertions.assertThat(test(openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:BB2]");
				Assertions.assertThat(test(openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.2.1")).toString()).isEqualTo("[1.1.2.1:1]");
				Assertions.assertThat(test(openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.3.1")).toString()).isEqualTo("[]");

				queue.finish().waitFor();
				Assertions.assertThat(openCount.count).isEqualTo(0L);

				LOGGER.debug("*** Cache 1 ***");

				try (SnmpCacheClient client = new SnmpCacheClient(openCount, queue, new Address(Address.LOCALHOST, 8080), delayToDiscard)) {
					diff[0] = "A";
					Assertions.assertThat(testCache(client, new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:A1, 1.1.1.2:A2, 1.1.1.3:A3]");
					diff[0] = "AA";
					Assertions.assertThat(testCache(client, new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:A2]");
					diff[0] = "B";
					Assertions.assertThat(testCache(client, new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:A1, 1.1.1.2:A2, 1.1.1.3:A3]");
					diff[0] = "BB";
					Assertions.assertThat(testCache(client, new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:A2]");
					Assertions.assertThat(testCache(client, new Oid("1.1.2.1")).toString()).isEqualTo("[1.1.2.1:1]");
					Assertions.assertThat(testCache(client, new Oid("1.1.3.1")).toString()).isEqualTo("[]");
				}

				queue.finish().waitFor();
				Assertions.assertThat(openCount.count).isEqualTo(0L);

				LOGGER.debug("*** Cache 2 ***");

				try (SnmpCacheClient client = new SnmpCacheClient(openCount, queue, new Address(Address.LOCALHOST, 8080), delayToDiscard)) {
					diff[0] = "AA";
					Assertions.assertThat(testCache(client, new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:AA2]");
					diff[0] = "A";
					Assertions.assertThat(testCache(client, new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:A1, 1.1.1.2:A2, 1.1.1.3:A3]");
					diff[0] = "BB";
					Assertions.assertThat(testCache(client, new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:AA2]");
					diff[0] = "B";
					Assertions.assertThat(testCache(client, new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:A1, 1.1.1.2:A2, 1.1.1.3:A3]");
					Assertions.assertThat(testCache(client, new Oid("1.1.2.1")).toString()).isEqualTo("[1.1.2.1:1]");
					Assertions.assertThat(testCache(client, new Oid("1.1.3.1")).toString()).isEqualTo("[]");
				}

				queue.finish().waitFor();
				Assertions.assertThat(openCount.count).isEqualTo(0L);

				LOGGER.debug("*** Cache 3 ***");

				try (SnmpCacheClient client = new SnmpCacheClient(openCount, queue, new Address(Address.LOCALHOST, 8080), delayToDiscard)) {
					diff[0] = "A";
					Assertions.assertThat(testCache(client, new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:A1, 1.1.1.2:A2, 1.1.1.3:A3]");
					diff[0] = "AA";
					Assertions.assertThat(testCache(client, new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:A2]");

					Thread.sleep((long) (delayToDiscard * 1000d * 1.1d));
					
					diff[0] = "B";
					Assertions.assertThat(testCache(client, new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:B1, 1.1.1.2:B2, 1.1.1.3:B3]");
					diff[0] = "BB";
					Assertions.assertThat(testCache(client, new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:B2]");
					
					Assertions.assertThat(testCache(client, new Oid("1.1.2.1")).toString()).isEqualTo("[1.1.2.1:1]");
					Assertions.assertThat(testCache(client, new Oid("1.1.3.1")).toString()).isEqualTo("[]");
				}

				queue.finish().waitFor();
				Assertions.assertThat(openCount.count).isEqualTo(0L);

			}
			queue.finish().waitFor();
		}

		Assertions.assertThat(serverOpenCount.count).isEqualTo(0L);
		Assertions.assertThat(openCount.count).isEqualTo(0L);

		try (Queue queue = new Queue()) {
			LOGGER.debug("*** Cache 4 ***");

			try (SnmpCacheClient client = new SnmpCacheClient(openCount, queue, new Address(Address.LOCALHOST, 8080), delayToDiscard)) {
				try {
					testCache(client, new Oid("1.1.1"));
					Assertions.fail("Should be impossible to connect");
				} catch (IOException ioe) {
				}

				Thread.sleep((long) (delayToDiscard * 1000d * 0.9d));

				try {
					testCache(client, new Oid("1.1.1"));
					Assertions.fail("Should be impossible to connect");
				} catch (IOException ioe) {
				}

				Thread.sleep((long) (delayToDiscard * 1000d * 1.1d));
				
				try {
					testCache(client, new Oid("1.1.1"));
					Assertions.fail("Should be impossible to connect");
				} catch (IOException ioe) {
				}
			}
			queue.finish().waitFor();
		}
		
		Assertions.assertThat(openCount.count).isEqualTo(0L);
	}
	
	private static List<Result> test(Count openCount, Address address, final Oid oid) throws IOException {
		final Lock<List<Result>, IOException> lock = new Lock<>();
		try (SnmpClient client = new Snmp().override(new CountingCurrentOpenReadyFactory(openCount, new DatagramReadyFactory())).to(new Address(Address.LOCALHOST, 8080)).client()) {
			client.connect(new SnmpClientHandler() {
				@Override
				public void failed(IOException e) {
					lock.fail(e);
				}
				@Override
				public void close() {
					lock.fail(new IOException("Closed"));
				}
				@Override
				public void launched(final Callback callback) {
					LOGGER.debug("Calling {}", oid);
					callback.get(oid, new SnmpClientHandler.Callback.GetCallback() {
						private final List<Result> r = new LinkedList<>();
						@Override
						public void failed(IOException e) {
							callback.close();
							lock.fail(e);
						}
						@Override
						public void close() {
							callback.close();
							lock.set(r);
						}
						@Override
						public void result(Result result) {
							r.add(result);
						}
					});
				}
			});
			return lock.waitFor();
		}
	}
	
	private static List<Result> testCache(SnmpCacheClient client, final Oid oid) throws IOException {
		LOGGER.debug("---> {}", oid);
		final Lock<List<Result>, IOException> lock = new Lock<>();
		client.get(oid, new SnmpClientHandler.Callback.GetCallback() {
			private final List<Result> r = new LinkedList<>();
			@Override
			public void failed(IOException e) {
				lock.fail(e);
			}
			@Override
			public void close() {
				lock.set(r);
			}
			@Override
			public void result(Result result) {
				r.add(result);
			}
		});
		return lock.waitFor();
	}
	
	private static final class SnmpCacheClient implements SnmpClientHandler.Callback, AutoCloseable {
		
		// Memory saving!
		private final Map<Oid, Oid> internOids = new HashMap<>();
		private final Map<String, String> internStrings = new HashMap<>();
		
		private Oid internOid(Oid oid) {
			Oid intern = internOids.get(oid);
			if (intern == null) {
				intern = oid;
				internOids.put(intern, intern);
			}
			return intern;
		}
		private String internString(String s) {
			String intern = internStrings.get(s);
			if (intern == null) {
				intern = s;
				internStrings.put(intern, intern);
			}
			return intern;
		}
		private Result internResult(Result result) {
			return new Result(internOid(result.getOid()), internString(result.getValue()));
		}
		
		
		private static final class CacheElement {
			public final List<Result> results = new LinkedList<>();
			public List<GetCallback> callbacks = new LinkedList<>();
			public boolean calling = false;
			public final double timestamp;
			public CacheElement(double timestamp) {
				this.timestamp = timestamp;
			}
		}
		private final Map<Oid, CacheElement> cache = new HashMap<>();

		private final Queue queue;
		private final SnmpClient client;
		private SnmpClientHandler.Callback callback = null;
		private boolean connecting = false;
		private IOException failed = null;
		public double timestamp;

		private final double delayToDiscard;
		
		public SnmpCacheClient(Count openCount, Queue queue, Address address, double delayToDiscard) {
			this.queue = queue;
			this.delayToDiscard = delayToDiscard;
			client = new Snmp().override(new CountingCurrentOpenReadyFactory(openCount, new DatagramReadyFactory())).withQueue(queue).to(address).client();
		}
		
		private void fail(IOException ioe) {
			failed = ioe;
			timestamp = now();
			for (CacheElement e : cache.values()) {
				if (e.callbacks != null) {
					for (GetCallback c : e.callbacks) {
						c.failed(ioe);
					}
				}
			}
			cache.clear();
		}
		
		@Override
		public void close() {
			client.close();
			queue.post(new Runnable() {
				@Override
				public void run() {
					fail(new IOException("Closed"));
					if (callback != null) {
						callback.close();
					}
				}
			});
		}
		
		private static double now() {
			return System.currentTimeMillis() / 1000d;
		}
		
		@Override
		public void get(final Oid oid, final GetCallback getCallback) {
			queue.post(new Runnable() {
				@Override
				public void run() {
					double now = now();
					
					if (failed != null) {
						if ((now - timestamp) > delayToDiscard) {
							failed = null;
						}
					}
					if (failed != null) {
						getCallback.failed(failed);
						return;
					}
					
					CacheElement e = cache.get(oid);
					if (e != null) {
						if (e.callbacks == null) {
							if ((now - e.timestamp) > delayToDiscard) {
								e = null;
							}
						}
					}
					if (e == null) {
						e = new CacheElement(now);
						cache.put(internOid(oid), e);
					}
					
					if (e.callbacks == null) {
						for (Result r : e.results) {
							getCallback.result(r);
						}
						getCallback.close();
					} else {
						e.callbacks.add(getCallback);
						if (e.calling) {
							return;
						}
						
						if (callback == null) {
							if (connecting) {
								return;
							}
							LOGGER.debug("Connecting");
							connecting = true;
							client.connect(new SnmpClientHandler() {
								@Override
								public void failed(IOException ioe) {
									connecting = false;
									fail(ioe);
								}
								@Override
								public void launched(Callback callback) {
									connecting = false;
									SnmpCacheClient.this.callback = callback;
									for (Map.Entry<Oid, CacheElement> entry : cache.entrySet()) {
										Oid oid = entry.getKey();
										CacheElement e = entry.getValue();
										call(oid, e);
									}
								}
								@Override
								public void close() {
									callback = null;
									fail(new IOException("Closed by peer"));
								}
							});
						} else {
							call(oid, e);
						}
					}
				}
			});
		}
		
		private void call(Oid oid, CacheElement e) {
			LOGGER.debug("Actually calling {}", oid);
			e.calling = true;
			final CacheElement ee = e;
			callback.get(oid, new GetCallback() {
				@Override
				public void failed(IOException e) {
					close();
				}
				@Override
				public void close() {
					for (GetCallback c : ee.callbacks) {
						c.close();
					}
					ee.callbacks = null;
					ee.calling = false;
				}
				@Override
				public void result(Result result) {
					Result internResult = internResult(result);
					
					double now = now();

					// Cache single value
					Oid oid = result.getOid();
					CacheElement r = cache.get(oid);
					if (r != null) {
						if (r.callbacks == null) {
							if ((now - r.timestamp) > delayToDiscard) {
								r = null;
							}
						}
					}
					if (r == null) {
						r = new CacheElement(ee.timestamp);
						LOGGER.debug("Caching single value: {} = {}", oid, result);
						cache.put(internOid(oid), r);
						r.results.add(internResult);
						r.callbacks = null;
					}
					//

					ee.results.add(internResult);
					for (GetCallback c : ee.callbacks) {
						c.result(result);
					}
				}
			});
		}
	}

}
