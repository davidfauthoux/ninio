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
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.SqliteCache;
import com.davfx.ninio.core.UdpSocket;
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
			final Wait waitServer = new Wait();
			try (Disconnectable snmpServer = ninio.create(SnmpServer.builder().with(UdpSocket.builder().bind(new Address(Address.LOCALHOST, port)))
					.handle(new FromMapSnmpServerHandler(map, new SnmpServerHandler() {
						@Override
						public void from(Oid oid, Callback callback) {
						}
						@Override
						public void failed(IOException ioe) {
						}
						@Override
						public void connected(Address address) {
						}
						@Override
						public void closed() {
							waitServer.run();
						}
					})))) {
				final Wait waitClient = new Wait();
				try (SnmpConnecter snmpClient = ninio.create(SnmpClient.builder().with(UdpSocket.builder()).with(new SerialExecutor(SnmpTest.class)))) {
					snmpClient.connect(new SnmpConnection() {
							@Override
							public void failed(IOException ioe) {
							}
							@Override
							public void connected(Address address) {
							}
							@Override
							public void closed() {
								waitClient.run();
							}
						});
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.1")).toString()).isEqualTo("[1.1.1:val1.1.1]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1")).toString()).isEqualTo("[1.1.1:val1.1.1, 1.1.1.1:val1.1.1.1, 1.1.1.2:val1.1.1.2, 1.1.2:val1.1.2, 1.1.3.1:val1.1.3.1, 1.1.3.2:val1.1.3.2]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.2")).toString()).isEqualTo("[1.1.2:val1.1.2]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.3")).toString()).isEqualTo("[1.1.3.1:val1.1.3.1, 1.1.3.2:val1.1.3.2]");
					Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), new Oid("1.1.4")).toString()).isEqualTo("[]");
				}
				waitClient.waitFor();
			}
			waitServer.waitFor();
		}
	}
	
	private static List<SnmpResult> get(SnmpConnecter snmpClient, Address a, Oid oid) throws IOException {
		final Lock<List<SnmpResult>, IOException> lock = new Lock<>();
		snmpClient.get(a, "community", null, oid, new SnmpReceiver() {
			private final List<SnmpResult> r = new LinkedList<>();
			
			@Override
			public void received(SnmpResult result) {
				r.add(result);
			}
			
			@Override
			public void finished() {
				lock.set(r);
			}
			
			@Override
			public void failed(IOException ioe) {
				lock.fail(ioe);
			}
		});
		return lock.waitFor();
	}

	
	@Test
	public void testTimeout() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			int port = 8080;
			final Lock<String, IOException> lock = new Lock<>();
			try (SnmpConnecter snmpClient = SnmpTimeout.wrap(0.5d, ninio.create(SnmpClient.builder().with(UdpSocket.builder()).with(new SerialExecutor(SnmpTest.class))))) {
				snmpClient.connect(new SnmpConnection() {
						@Override
						public void failed(IOException ioe) {
						}
						@Override
						public void connected(Address address) {
						}
						@Override
						public void closed() {
						}
					});
				
				snmpClient.get(new Address(Address.LOCALHOST, port), "community", null, new Oid("1.1.1"), new SnmpReceiver() {
							@Override
							public void received(SnmpResult result) {
							}
							@Override
							public void finished() {
							}
							@Override
							public void failed(IOException ioe) {
								lock.set(ioe.getMessage());
							}
						});

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

			final SnmpServerHandler handler = new FromMapSnmpServerHandler(map, new SnmpServerHandler() {
				@Override
				public void from(Oid oid, Callback callback) {
				}
				@Override
				public void failed(IOException ioe) {
				}
				@Override
				public void connected(Address address) {
				}
				@Override
				public void closed() {
				}
			});
			
			int port = 8080;
			final Wait waitServer = new Wait();
			try (Disconnectable snmpServer = ninio.create(SnmpServer.builder().with(UdpSocket.builder().bind(new Address(Address.LOCALHOST, port)))
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
					@Override
					public void closed() {
						waitServer.run();
					}
					@Override
					public void connected(Address address) {
					}
					@Override
					public void failed(IOException ioe) {
					}
				}))) {
				try (SnmpConnecter snmpClient = ninio.create(SnmpClient.builder().with(new SerialExecutor(SnmpTest.class)).with(SqliteCache.<Integer>builder().using(new SnmpSqliteCacheInterpreter())))) {
					snmpClient.connect(new SnmpConnection() {
						@Override
						public void failed(IOException ioe) {
						}
						@Override
						public void connected(Address address) {
						}
						@Override
						public void closed() {
						}
					});
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
			}
			waitServer.waitFor();
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

			final SnmpServerHandler handler = new FromMapSnmpServerHandler(map, new SnmpServerHandler() {
				@Override
				public void from(Oid oid, Callback callback) {
				}
				@Override
				public void failed(IOException ioe) {
				}
				@Override
				public void connected(Address address) {
				}
				@Override
				public void closed() {
				}
			});
			
			int port = 8080;
			final Wait waitServer = new Wait();
			try (Disconnectable snmpServer = ninio.create(SnmpServer.builder().with(UdpSocket.builder().bind(new Address(Address.LOCALHOST, port)))
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
						@Override
						public void closed() {
							waitServer.run();
						}
						@Override
						public void connected(Address address) {
						}
						@Override
						public void failed(IOException ioe) {
						}
					}))) {
				final Wait waitClient = new Wait();
				try (SnmpConnecter snmpClient = ninio.create(SnmpClient.builder().with(UdpSocket.builder()).with(new SerialExecutor(SnmpTest.class)))) {
					snmpClient.connect(new SnmpConnection() {
							@Override
							public void failed(IOException ioe) {
							}
							@Override
							public void connected(Address address) {
							}
							@Override
							public void closed() {
								waitClient.run();
							}
						});
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
