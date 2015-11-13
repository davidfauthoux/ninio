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
import com.davfx.ninio.snmp.SnmpClientHandler.Callback.GetCallback;
import com.davfx.ninio.util.GlobalQueue;
import com.davfx.util.Lock;

public class TestSnmpServer {

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
		
		{
			try (SnmpServer snmpServer = new SnmpServer(GlobalQueue.get(), new CountingCurrentOpenReady(serverOpenCount, new DatagramReady(GlobalQueue.get().getSelector(), GlobalQueue.get().allocator()).bind()), new Address(Address.LOCALHOST, 8080), SnmpServerUtils.from(map))) {
				{
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
								callback.get(new Oid("1.1.1"), new GetCallback() {
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
						Assertions.assertThat(lock.waitFor().toString()).isEqualTo("[1.1.1:val1.1.1]");
					}
				}

				Assertions.assertThat(test(openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1]");
				Assertions.assertThat(test(openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1]");
				Assertions.assertThat(test(openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1")).toString()).isEqualTo("[1.1.1:val1.1.1, 1.1.1.1:val1.1.1.1, 1.1.1.2:val1.1.1.2, 1.1.2:val1.1.2, 1.1.3.1:val1.1.3.1, 1.1.3.2:val1.1.3.2]");
				Assertions.assertThat(test(openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.2")).toString()).isEqualTo("[1.1.2:val1.1.2]");
				Assertions.assertThat(test(openCount, new Address(Address.LOCALHOST, 8080), new Oid("1.1.3")).toString()).isEqualTo("[1.1.3.1:val1.1.3.1, 1.1.3.2:val1.1.3.2]");
			}
		}
		
		GlobalQueue.get().finish().waitFor();
		
		Assertions.assertThat(serverOpenCount.count).isEqualTo(0L);
		Assertions.assertThat(openCount.count).isEqualTo(0L);
	}
	
	private static List<Result> test(Count openCount, Address address, Oid oid) throws IOException {
		final Lock<List<Result>, IOException> lock = new Lock<>();
		new Snmp().override(new CountingCurrentOpenReadyFactory(openCount, new DatagramReadyFactory())).to(address).get(oid, new GetCallback() {
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
