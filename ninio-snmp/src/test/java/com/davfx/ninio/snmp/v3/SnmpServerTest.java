package com.davfx.ninio.snmp.v3;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.Ninio;
import com.davfx.ninio.core.v3.Shared;
import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.Result;
import com.davfx.util.Lock;

public class SnmpServerTest {
	
	@Test
	public void test() throws Exception {
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
				final Lock<List<Result>, IOException> lock = new Lock<>();
				SnmpClient snmpClient = ninio.create(SnmpClient.builder());
				try {
						snmpClient
						.request()
						.receiving(new SnmpReceiver() {
							private final List<Result> r = new LinkedList<>();
							@Override
							public void received(Result result) {
								r.add(result);
							}
							@Override
							public void finished() {
								lock.set(r);
							}
						})
						.failing(new Failing() {
							@Override
							public void failed(IOException e) {
								lock.fail(e);
							}
						})
						.build()
						.get(new Address(Address.LOCALHOST, port), "community", null, new Oid("1.1.1"));
					lock.waitFor();
				} finally {
					snmpClient.close();
				}
				Assertions.assertThat(lock.waitFor().toString()).isEqualTo("[1.1.1:val1.1.1]");
			} finally {
				snmpServer.close();
			}
		}
	}
	
	@Test
	public void testTimeout() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			int port = 8080;
			final Lock<String, IOException> lock = new Lock<>();
			SnmpClient snmpClient = ninio.create(SnmpClient.builder());
			try {
				SnmpReceiverRequestBuilder request = snmpClient.request().failing(new Failing() {
					@Override
					public void failed(IOException e) {
						lock.set(e.getMessage());
					}
				});
				request = SnmpTimeout.hook(Shared.EXECUTOR, request, 0.25d);
				request.build().get(new Address(Address.LOCALHOST, port), "community", null, new Oid("1.1.1"));
				lock.waitFor();
			} finally {
				snmpClient.close();
			}
			Assertions.assertThat(lock.waitFor()).isEqualTo("Timeout");
		}
	}
}
