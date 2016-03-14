package com.davfx.ninio.snmp.v3;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Count;
import com.davfx.ninio.core.CountingCurrentOpenReady;
import com.davfx.ninio.core.DatagramReady;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.Result;
import com.davfx.ninio.snmp.SnmpServer;
import com.davfx.ninio.snmp.SnmpServerUtils;
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
		
		int port = 8080;
		try (Queue queue = new Queue()) {
			try (SnmpServer snmpServer = new SnmpServer(queue, new CountingCurrentOpenReady(serverOpenCount, new DatagramReady(queue.getSelector(), queue.allocator()).bind()), new Address(Address.LOCALHOST, port), SnmpServerUtils.from(map))) {
				queue.finish().waitFor();
				final Lock<List<Result>, IOException> lock = new Lock<>();
				try (SnmpClient snmpClient = new SnmpClient()) {
					SnmpRequest r = snmpClient.create();
					r.receiving(new SnmpReceiver() {
						private final List<Result> r = new LinkedList<>();
						@Override
						public void received(Result result) {
							r.add(result);
						}
						@Override
						public void finished() {
							lock.set(r);
						}
					});
					r.failing(new Failing() {
						@Override
						public void failed(IOException e) {
							lock.fail(e);
						}
					});
					r.get(new Address(Address.LOCALHOST, port), "community", null, new Oid("1.1.1"));
					lock.waitFor();
				}
				Assertions.assertThat(lock.waitFor().toString()).isEqualTo("[1.1.1:val1.1.1]");
			}
			queue.finish().waitFor();
		}
		
		Assertions.assertThat(serverOpenCount.count).isEqualTo(0L);
		Assertions.assertThat(openCount.count).isEqualTo(0L);
	}
	
}
