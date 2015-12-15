package com.davfx.ninio.snmp;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

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
		
		final double delayToDiscard = 1d;
		SnmpCache.Filter oidFilter = new SnmpCache.Filter() {
			@Override
			public double cache(Address address, Oid oid) {
				return delayToDiscard;
			}
		};
		
		Address address = new Address(Address.LOCALHOST, 8080);

		try (Queue queue = new Queue()) {
			final String[] diff = new String[] { null };
			try (SnmpServer snmpServer = new SnmpServer(queue, new CountingCurrentOpenReady(serverOpenCount, new DatagramReady(queue.getSelector(), queue.allocator()).bind()), address, new SnmpServer.Handler() {
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
				Assertions.assertThat(test(queue, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:A1, 1.1.1.2:A2, 1.1.1.3:A3]");
				diff[0] = "AA";
				Assertions.assertThat(test(queue, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:AA2]");
				diff[0] = "B";
				Assertions.assertThat(test(queue, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:B1, 1.1.1.2:B2, 1.1.1.3:B3]");
				diff[0] = "BB";
				Assertions.assertThat(test(queue, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:BB2]");
				Assertions.assertThat(test(queue, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.2.1")).toString()).isEqualTo("[1.1.2.1:1]");
				Assertions.assertThat(test(queue, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.3.1")).toString()).isEqualTo("[]");

				queue.finish().waitFor();
				Assertions.assertThat(openCount.count).isEqualTo(0L);

				LOGGER.debug("*** Cache 1 ***");

				{
					SnmpCache client = new SnmpCache(queue, new CountingCurrentOpenReadyFactory(openCount, new DatagramReadyFactory(queue)), oidFilter);
					diff[0] = "A";
					Assertions.assertThat(testCache(client, address, new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:A1, 1.1.1.2:A2, 1.1.1.3:A3]");
					diff[0] = "AA";
					Assertions.assertThat(testCache(client, address, new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:A2]");
					diff[0] = "B";
					Assertions.assertThat(testCache(client, address, new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:A1, 1.1.1.2:A2, 1.1.1.3:A3]");
					diff[0] = "BB";
					Assertions.assertThat(testCache(client, address, new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:A2]");
					Assertions.assertThat(testCache(client, address, new Oid("1.1.2.1")).toString()).isEqualTo("[1.1.2.1:1]");
					Assertions.assertThat(testCache(client, address, new Oid("1.1.3.1")).toString()).isEqualTo("[]");
				}

				queue.finish().waitFor();
				Assertions.assertThat(openCount.count).isEqualTo(0L);

				LOGGER.debug("*** Cache 2 ***");

				{
					SnmpCache client = new SnmpCache(queue, new CountingCurrentOpenReadyFactory(openCount, new DatagramReadyFactory(queue)), oidFilter);
					diff[0] = "AA";
					Assertions.assertThat(testCache(client, address, new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:AA2]");
					diff[0] = "A";
					Assertions.assertThat(testCache(client, address, new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:A1, 1.1.1.2:A2, 1.1.1.3:A3]");
					diff[0] = "BB";
					Assertions.assertThat(testCache(client, address, new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:AA2]");
					diff[0] = "B";
					Assertions.assertThat(testCache(client, address, new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:A1, 1.1.1.2:A2, 1.1.1.3:A3]");
					Assertions.assertThat(testCache(client, address, new Oid("1.1.2.1")).toString()).isEqualTo("[1.1.2.1:1]");
					Assertions.assertThat(testCache(client, address, new Oid("1.1.3.1")).toString()).isEqualTo("[]");
				}

				queue.finish().waitFor();
				Assertions.assertThat(openCount.count).isEqualTo(0L);

				LOGGER.debug("*** Cache 3 ***");

				{
					SnmpCache client = new SnmpCache(queue, new CountingCurrentOpenReadyFactory(openCount, new DatagramReadyFactory(queue)), oidFilter);
					diff[0] = "A";
					Assertions.assertThat(testCache(client, address, new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:A1, 1.1.1.2:A2, 1.1.1.3:A3]");
					diff[0] = "AA";
					Assertions.assertThat(testCache(client, address, new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:A2]");

					Thread.sleep((long) (delayToDiscard * 1000d * 1.1d));
					
					diff[0] = "B";
					Assertions.assertThat(testCache(client, address, new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:B1, 1.1.1.2:B2, 1.1.1.3:B3]");
					diff[0] = "BB";
					Assertions.assertThat(testCache(client, address, new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:B2]");
					
					Assertions.assertThat(testCache(client, address, new Oid("1.1.2.1")).toString()).isEqualTo("[1.1.2.1:1]");
					Assertions.assertThat(testCache(client, address, new Oid("1.1.3.1")).toString()).isEqualTo("[]");
				}

				LOGGER.debug("*** Cache 3b ***");

				{
					SnmpCache client = new SnmpCache(queue, new CountingCurrentOpenReadyFactory(openCount, new DatagramReadyFactory(queue)), oidFilter);
					diff[0] = "A";
					Assertions.assertThat(testCache(client, address, new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:A1, 1.1.1.2:A2, 1.1.1.3:A3]");
					diff[0] = "AA";
					Assertions.assertThat(testCache(client, address, new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:A2]");

					client.clear();
					
					diff[0] = "B";
					Assertions.assertThat(testCache(client, address, new Oid("1.1.1")).toString()).isEqualTo("[1.1.1.1:B1, 1.1.1.2:B2, 1.1.1.3:B3]");
					diff[0] = "BB";
					Assertions.assertThat(testCache(client, address, new Oid("1.1.1.2")).toString()).isEqualTo("[1.1.1.2:B2]");
					
					Assertions.assertThat(testCache(client, address, new Oid("1.1.2.1")).toString()).isEqualTo("[1.1.2.1:1]");
					Assertions.assertThat(testCache(client, address, new Oid("1.1.3.1")).toString()).isEqualTo("[]");
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

			{
				SnmpCache client = new SnmpCache(queue, new CountingCurrentOpenReadyFactory(openCount, new DatagramReadyFactory(queue)), oidFilter);
				try {
					testCache(client, address, new Oid("1.1.1"));
					Assertions.fail("Should be impossible to connect");
				} catch (IOException ioe) {
				}

				Thread.sleep((long) (delayToDiscard * 1000d * 0.9d));

				try {
					testCache(client, address, new Oid("1.1.1"));
					Assertions.fail("Should be impossible to connect");
				} catch (IOException ioe) {
				}

				Thread.sleep((long) (delayToDiscard * 1000d * 1.1d));
				
				try {
					testCache(client, address, new Oid("1.1.1"));
					Assertions.fail("Should be impossible to connect");
				} catch (IOException ioe) {
				}
			}
			queue.finish().waitFor();
		}
		
		Assertions.assertThat(openCount.count).isEqualTo(0L);
	}
	
	private static List<Result> test(Queue queue, Count openCount, Address address, final Oid oid) throws IOException {
		final Lock<List<Result>, IOException> lock = new Lock<>();
		try (SnmpClient client = new Snmp().override(new CountingCurrentOpenReadyFactory(openCount, new DatagramReadyFactory(queue))).to(new Address(Address.LOCALHOST, 8080)).client()) {
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
					LOGGER.trace("Calling {}", oid);
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
	
	private static List<Result> testCache(SnmpCache client, Address address, Oid oid) throws IOException {
		final Lock<List<Result>, IOException> lock = new Lock<>();
		client.get(address, oid, new SnmpClientHandler.Callback.GetCallback() {
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
	
}
