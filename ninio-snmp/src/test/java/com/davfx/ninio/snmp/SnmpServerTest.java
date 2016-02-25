package com.davfx.ninio.snmp;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Count;
import com.davfx.ninio.core.CountingCurrentOpenReady;
import com.davfx.ninio.core.CountingCurrentOpenReadyFactory;
import com.davfx.ninio.core.DatagramReady;
import com.davfx.ninio.core.DatagramReadyFactory;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.util.Lock;

public class SnmpServerTest {
	
	private static final class InnerCount implements Count {
		public long count = 0L;
		public InnerCount() {
		}
		@Override
		public void inc(long delta) {
			count += delta;
		}
	}
	
	@Test
	public void test() throws Exception {
		TreeMap<Oid, String> map = new TreeMap<>();
		map.put(new Oid("1.1.1"), "val1.1.1");
		map.put(new Oid("1.1.1.1"), "val1.1.1.1");
		map.put(new Oid("1.1.1.2"), "val1.1.1.2");
		map.put(new Oid("1.1.2"), "val1.1.2");
		map.put(new Oid("1.1.3.1"), "val1.1.3.1");
		map.put(new Oid("1.1.3.2"), "val1.1.3.2");
		
		InnerCount serverOpenCount = new InnerCount();
		InnerCount openCount = new InnerCount();
		
		try (Queue queue = new Queue()) {
			try (SnmpServer snmpServer = new SnmpServer(queue, new CountingCurrentOpenReady(serverOpenCount, new DatagramReady(queue.getSelector(), queue.allocator()).bind()), new Address(Address.LOCALHOST, 8080), SnmpServerUtils.from(map))) {
				queue.finish().waitFor();
				final Lock<List<Result>, IOException> lock = new Lock<>();
				new Snmp().override(new CountingCurrentOpenReadyFactory(openCount, new DatagramReadyFactory(queue))).to(new Address(Address.LOCALHOST, 8080)).get(new Oid("1.1.1"), new SnmpClientHandler.Callback.GetCallback() {
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
				Assertions.assertThat(lock.waitFor().toString()).isEqualTo("[1.1.1:val1.1.1]");

				Assertions.assertThat(test(queue, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1]");
				Assertions.assertThat(test(queue, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1]");
				Assertions.assertThat(test(queue, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1")).toString()).isEqualTo("[1.1.1:val1.1.1, 1.1.1.1:val1.1.1.1, 1.1.1.2:val1.1.1.2, 1.1.2:val1.1.2, 1.1.3.1:val1.1.3.1, 1.1.3.2:val1.1.3.2]");
				Assertions.assertThat(test(queue, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.2")).toString()).isEqualTo("[1.1.2:val1.1.2]");
				Assertions.assertThat(test(queue, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.3")).toString()).isEqualTo("[1.1.3.1:val1.1.3.1, 1.1.3.2:val1.1.3.2]");
			}
			queue.finish().waitFor();
		}
		
		Assertions.assertThat(serverOpenCount.count).isEqualTo(0L);
		Assertions.assertThat(openCount.count).isEqualTo(0L);
	}
	
	private static List<Result> test(Queue queue, Count openCount, Address address, Oid oid) throws IOException {
		final Lock<List<Result>, IOException> lock = new Lock<>();
		new Snmp().override(new CountingCurrentOpenReadyFactory(openCount, new DatagramReadyFactory(queue))).to(address).get(oid, new SnmpClientHandler.Callback.GetCallback() {
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

	@Test
	public void testSimultaneous() throws Exception {
		TreeMap<Oid, String> map = new TreeMap<>();
		map.put(new Oid("1.1.1"), "val1.1.1");
		map.put(new Oid("1.1.1.1"), "val1.1.1.1");
		map.put(new Oid("1.1.1.2"), "val1.1.1.2");
		map.put(new Oid("1.1.2"), "val1.1.2");
		map.put(new Oid("1.1.3.1"), "val1.1.3.1");
		map.put(new Oid("1.1.3.2"), "val1.1.3.2");
		
		InnerCount serverOpenCount = new InnerCount();
		InnerCount openCount = new InnerCount();
		
		try (Queue queue = new Queue()) {
			ReadyFactory readyFactory = new DatagramReadyFactory(queue);
			try (SnmpServer snmpServer = new SnmpServer(queue, new CountingCurrentOpenReady(serverOpenCount, new DatagramReady(queue.getSelector(), queue.allocator()).bind()), new Address(Address.LOCALHOST, 8080), SnmpServerUtils.from(map))) {
				queue.finish().waitFor();
				final Lock<List<Result>, IOException> lock = new Lock<>();
				new Snmp().override(new CountingCurrentOpenReadyFactory(openCount, readyFactory)).to(new Address(Address.LOCALHOST, 8080)).get(new Oid("1.1.1"), new SnmpClientHandler.Callback.GetCallback() {
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
				Assertions.assertThat(lock.waitFor().toString()).isEqualTo("[1.1.1:val1.1.1]");

				List<Lock<List<Result>, IOException>> l = new LinkedList<>();
				l.add(testSimultaneous(readyFactory, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.1")));
				l.add(testSimultaneous(readyFactory, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.1")));
				l.add(testSimultaneous(readyFactory, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1")));
				l.add(testSimultaneous(readyFactory, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.2")));
				l.add(testSimultaneous(readyFactory, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.3")));
				Assertions.assertThat(l.get(0).waitFor().toString()).isEqualTo("[1.1.1:val1.1.1]");
				Assertions.assertThat(l.get(1).waitFor().toString()).isEqualTo("[1.1.1:val1.1.1]");
				Assertions.assertThat(l.get(2).waitFor().toString()).isEqualTo("[1.1.1:val1.1.1, 1.1.1.1:val1.1.1.1, 1.1.1.2:val1.1.1.2, 1.1.2:val1.1.2, 1.1.3.1:val1.1.3.1, 1.1.3.2:val1.1.3.2]");
				Assertions.assertThat(l.get(3).waitFor().toString()).isEqualTo("[1.1.2:val1.1.2]");
				Assertions.assertThat(l.get(4).waitFor().toString()).isEqualTo("[1.1.3.1:val1.1.3.1, 1.1.3.2:val1.1.3.2]");
				
			}
			queue.finish().waitFor();
		}
		
		Assertions.assertThat(serverOpenCount.count).isEqualTo(0L);
		Assertions.assertThat(openCount.count).isEqualTo(0L);
	}
	
	private static Lock<List<Result>, IOException> testSimultaneous(ReadyFactory readyFactory, Count openCount, Address address, Oid oid) throws IOException {
		final Lock<List<Result>, IOException> lock = new Lock<>();
		new Snmp().override(new CountingCurrentOpenReadyFactory(openCount, readyFactory)).to(address).get(oid, new SnmpClientHandler.Callback.GetCallback() {
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
		return lock;
	}

	/*%%
	@Ignore //TO DO
	@Test
	public void testSimultaneousWithTcpdump() throws Exception {
		TreeMap<Oid, String> map = new TreeMap<>();
		map.put(new Oid("1.1.1"), "val1.1.1");
		map.put(new Oid("1.1.1.1"), "val1.1.1.1");
		map.put(new Oid("1.1.1.2"), "val1.1.1.2");
		map.put(new Oid("1.1.2"), "val1.1.2");
		map.put(new Oid("1.1.3.1"), "val1.1.3.1");
		map.put(new Oid("1.1.3.2"), "val1.1.3.2");
		
		InnerCount serverOpenCount = new InnerCount();
		InnerCount openCount = new InnerCount();
		
		String interfaceId = "lo0";
		
		try (Queue queue = new Queue()) {
			try (TcpdumpSyncDatagramReady.Receiver receiver = new TcpdumpSyncDatagramReady.Receiver(new TcpdumpSyncDatagramReady.DestinationPortRule(8080), interfaceId)) {
				ReadyFactory readyFactory = new TcpdumpSyncDatagramReadyFactory(queue, receiver);
				try (SnmpServer snmpServer = new SnmpServer(queue, new CountingCurrentOpenReady(serverOpenCount, new DatagramReady(queue.getSelector(), queue.allocator()).bind()), new Address(Address.LOCALHOST, 8080), SnmpServerUtils.from(map))) {
					queue.finish().waitFor();
					final Lock<List<Result>, IOException> lock = new Lock<>();
					new Snmp().override(new CountingCurrentOpenReadyFactory(openCount, readyFactory)).to(new Address(Address.LOCALHOST, 8080)).get(new Oid("1.1.1"), new SnmpClientHandler.Callback.GetCallback() {
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
					Assertions.assertThat(lock.waitFor().toString()).isEqualTo("[1.1.1:val1.1.1]");
	
					List<Lock<List<Result>, IOException>> l = new LinkedList<>();
					l.add(testSimultaneous(readyFactory, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.1")));
					l.add(testSimultaneous(readyFactory, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.1")));
					l.add(testSimultaneous(readyFactory, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1")));
					l.add(testSimultaneous(readyFactory, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.2")));
					l.add(testSimultaneous(readyFactory, openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.3")));
					Assertions.assertThat(l.get(0).waitFor().toString()).isEqualTo("[1.1.1:val1.1.1]");
					Assertions.assertThat(l.get(1).waitFor().toString()).isEqualTo("[1.1.1:val1.1.1]");
					Assertions.assertThat(l.get(2).waitFor().toString()).isEqualTo("[1.1.1:val1.1.1, 1.1.1.1:val1.1.1.1, 1.1.1.2:val1.1.1.2, 1.1.2:val1.1.2, 1.1.3.1:val1.1.3.1, 1.1.3.2:val1.1.3.2]");
					Assertions.assertThat(l.get(3).waitFor().toString()).isEqualTo("[1.1.2:val1.1.2]");
					Assertions.assertThat(l.get(4).waitFor().toString()).isEqualTo("[1.1.3.1:val1.1.3.1, 1.1.3.2:val1.1.3.2]");
					
				}
			}
			queue.finish().waitFor();
		}
		
		Assertions.assertThat(serverOpenCount.count).isEqualTo(0L);
		Assertions.assertThat(openCount.count).isEqualTo(0L);
	}
	*/
	
}
