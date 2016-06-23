package com.davfx.ninio.snmp.v3;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.LockFailing;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.Timeout;
import com.davfx.ninio.snmp.FromMapSnmpServerHandler;
import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.SnmpClient;
import com.davfx.ninio.snmp.SnmpReceiver;
import com.davfx.ninio.snmp.SnmpResult;
import com.davfx.ninio.snmp.SnmpServer;
import com.davfx.ninio.snmp.SnmpTimeout;
import com.davfx.ninio.util.Lock;

public class SnmpServerTest {
	
	@Test
	public void test() throws Exception {
		final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		try (Ninio ninio = Ninio.create()) {
			TreeMap<Oid, String> map = new TreeMap<>();
			map.put(new Oid("1.1.1"), "val1.1.1");
			map.put(new Oid("1.1.1.1"), "val1.1.1.1");
			map.put(new Oid("1.1.1.2"), "val1.1.1.2");
			map.put(new Oid("1.1.2"), "val1.1.2");
			map.put(new Oid("1.1.3.1"), "val1.1.3.1");
			map.put(new Oid("1.1.3.2"), "val1.1.3.2");
			
			int port = 8080;
			Disconnectable snmpServer = ninio.create(SnmpServer.builder()
					.bind(new Address(Address.LOCALHOST, port))
					.handle(new FromMapSnmpServerHandler(map)));
			try {
				final Lock<List<SnmpResult>, IOException> lock = new Lock<>();
				try (SnmpClient snmpClient = ninio.create(SnmpClient.builder().with(executor))) {
					snmpClient
						.request()
						.receiving(new SnmpReceiver() {
							private final List<SnmpResult> r = new LinkedList<>();
							@Override
							public void received(SnmpResult result) {
								r.add(result);
							}
							@Override
							public void finished() {
								lock.set(r);
							}
						})
						.failing(new LockFailing(lock))
						.get(new Address(Address.LOCALHOST, port), "community", null, new Oid("1.1.1"));

					Assertions.assertThat(lock.waitFor().toString()).isEqualTo("[1.1.1:val1.1.1]");
				}
			} finally {
				snmpServer.close();
			}
		}
	}
	
	@Test
	public void testTimeout() throws Exception {
		final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			int port = 8080;
			final Lock<String, IOException> lock = new Lock<>();
			try (SnmpClient snmpClient = ninio.create(SnmpClient.builder().with(executor))) {
				SnmpTimeout.wrap(timeout, 0.5d, snmpClient
					.request())
					.failing(new Failing() {
						@Override
						public void failed(IOException e) {
							lock.set(e.getMessage());
						}
					})
					.get(new Address(Address.LOCALHOST, port), "community", null, new Oid("1.1.1"));

				Assertions.assertThat(lock.waitFor()).isEqualTo("Timeout");
			}
		}
	}
}
