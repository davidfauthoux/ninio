package com.davfx.ninio.snmp;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.LockFailing;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.SqliteCache;
import com.davfx.ninio.core.Timeout;
import com.davfx.ninio.core.UdpSocket;
import com.davfx.ninio.core.WaitClosing;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.SerialExecutor;
import com.davfx.ninio.util.Wait;

public class SnmpTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpTest.class);
	
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
				try (SnmpClient snmpClient = ninio.create(SnmpClient.builder().with(new SerialExecutor(SnmpTest.class)))) {
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1")).toString()).isEqualTo("[1.1.1:val1.1.1, 1.1.1.1:val1.1.1.1, 1.1.1.2:val1.1.1.2, 1.1.2:val1.1.2, 1.1.3.1:val1.1.3.1, 1.1.3.2:val1.1.3.2]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.2")).toString()).isEqualTo("[1.1.2:val1.1.2]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.3")).toString()).isEqualTo("[1.1.3.1:val1.1.3.1, 1.1.3.2:val1.1.3.2]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.4")).toString()).isEqualTo("[]");
				}
			} finally {
				snmpServer.close();
			}
		}
	}
	
	private static List<SnmpResult> get(SnmpClient snmpClient, Address a, Oid oid) throws IOException {
		final Lock<List<SnmpResult>, IOException> lock = new Lock<>();
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
			.get(a, "community", null, oid);
		return lock.waitFor();
	}

	
	@Test
	public void testTimeout() throws Exception {
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			int port = 8080;
			final Lock<String, IOException> lock = new Lock<>();
			try (SnmpClient snmpClient = ninio.create(SnmpClient.builder().with(new SerialExecutor(SnmpTest.class)))) {
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
	
	@Test
	public void testWithCache() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			TreeMap<Oid, String> map = new TreeMap<>();
			map.put(new Oid("1.1.1"), "val1.1.1");
			map.put(new Oid("1.1.1.1"), "val1.1.1.1");
			map.put(new Oid("1.1.1.2"), "val1.1.1.2");
			map.put(new Oid("1.1.2"), "val1.1.2");
			map.put(new Oid("1.1.3.1"), "val1.1.3.1");
			map.put(new Oid("1.1.3.2"), "val1.1.3.2");

			final SnmpServerHandler handler = new FromMapSnmpServerHandler(map);
			
			int port = 8080;
			Disconnectable snmpServer = ninio.create(SnmpServer.builder()
					.bind(new Address(Address.LOCALHOST, port))
					.handle(new SnmpServerHandler() {
						private int n = 0;
						private Thread thread = null;
						@Override
						public void from(Oid oid, final Callback callback) {
							if (thread == null) {
								thread = Thread.currentThread();
							} else {
								if (thread != Thread.currentThread()) {
									throw new RuntimeException();
								}
							}

							final int k = n;
							LOGGER.debug("******* {} {}", oid, k);
							n++;
							handler.from(oid, new Callback() {
								@Override
								public boolean handle(SnmpResult result) {
									return callback.handle(new SnmpResult(result.oid, result.value + "/" + k));
								}
							});
						}
					}));
			try {
				try (SnmpClient snmpClient = ninio.create(SnmpClient.builder().with(new SerialExecutor(SnmpTest.class)).with(SqliteCache.<Integer>builder().using(new SnmpSqliteCacheInterpreter())))) {
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1/0]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1/0]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1")).toString()).isEqualTo("[1.1.1:val1.1.1/2, 1.1.1.1:val1.1.1.1/2, 1.1.1.2:val1.1.1.2/2, 1.1.2:val1.1.2/2, 1.1.3.1:val1.1.3.1/2, 1.1.3.2:val1.1.3.2/2]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1")).toString()).isEqualTo("[1.1.1:val1.1.1/2, 1.1.1.1:val1.1.1.1/2, 1.1.1.2:val1.1.1.2/2, 1.1.2:val1.1.2/2, 1.1.3.1:val1.1.3.1/2, 1.1.3.2:val1.1.3.2/2]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.2")).toString()).isEqualTo("[1.1.2:val1.1.2/4]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.2")).toString()).isEqualTo("[1.1.2:val1.1.2/4]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.3")).toString()).isEqualTo("[1.1.3.1:val1.1.3.1/6, 1.1.3.2:val1.1.3.2/6]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.3")).toString()).isEqualTo("[1.1.3.1:val1.1.3.1/6, 1.1.3.2:val1.1.3.2/6]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.4")).toString()).isEqualTo("[]");
				}
			} finally {
				snmpServer.close();
			}
		}
	}
	
	@Test
	public void testWithNoCache() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			TreeMap<Oid, String> map = new TreeMap<>();
			map.put(new Oid("1.1.1"), "val1.1.1");
			map.put(new Oid("1.1.1.1"), "val1.1.1.1");
			map.put(new Oid("1.1.1.2"), "val1.1.1.2");
			map.put(new Oid("1.1.2"), "val1.1.2");
			map.put(new Oid("1.1.3.1"), "val1.1.3.1");
			map.put(new Oid("1.1.3.2"), "val1.1.3.2");

			final SnmpServerHandler handler = new FromMapSnmpServerHandler(map);
			
			int port = 8080;
			Wait waitServer = new Wait();
			try (Disconnectable snmpServer = ninio.create(SnmpServer.builder().with(UdpSocket.builder().closing(new WaitClosing(waitServer)))
					.bind(new Address(Address.LOCALHOST, port))
					.handle(new SnmpServerHandler() {
						private int n = 0;
						private Thread thread = null;
						@Override
						public void from(Oid oid, final Callback callback) {
							if (thread == null) {
								thread = Thread.currentThread();
							} else {
								if (thread != Thread.currentThread()) {
									throw new RuntimeException();
								}
							}
							
							final int k = n;
							LOGGER.debug("******* {} {}", oid, k);
							n++;
							handler.from(oid, new Callback() {
								@Override
								public boolean handle(SnmpResult result) {
									return callback.handle(new SnmpResult(result.oid, result.value + "/" + k));
								}
							});
						}
					}))) {
				Wait waitClient = new Wait();
				try (SnmpClient snmpClient = ninio.create(SnmpClient.builder().with(UdpSocket.builder().closing(new WaitClosing(waitClient))).with(new SerialExecutor(SnmpTest.class)))) {
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1/0]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1/1]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1")).toString()).isEqualTo("[1.1.1:val1.1.1/3, 1.1.1.1:val1.1.1.1/3, 1.1.1.2:val1.1.1.2/3, 1.1.2:val1.1.2/3, 1.1.3.1:val1.1.3.1/3, 1.1.3.2:val1.1.3.2/3]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1")).toString()).isEqualTo("[1.1.1:val1.1.1/6, 1.1.1.1:val1.1.1.1/6, 1.1.1.2:val1.1.1.2/6, 1.1.2:val1.1.2/6, 1.1.3.1:val1.1.3.1/6, 1.1.3.2:val1.1.3.2/6]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.2")).toString()).isEqualTo("[1.1.2:val1.1.2/8]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.2")).toString()).isEqualTo("[1.1.2:val1.1.2/9]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.3")).toString()).isEqualTo("[1.1.3.1:val1.1.3.1/11, 1.1.3.2:val1.1.3.2/11]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.3")).toString()).isEqualTo("[1.1.3.1:val1.1.3.1/14, 1.1.3.2:val1.1.3.2/14]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.4")).toString()).isEqualTo("[]");
				}
				waitClient.waitFor();
			}
			waitServer.waitFor();
		}
	}

}
