package com.davfx.ninio.snmp;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.InMemoryCache;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.UdpSocket;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;

@Ignore
public class CacheTest {
	
	private static final Oid OID = new Oid("1.1.1");
	
	@Test
	public void testWithCache() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			final SnmpServerHandler handler = new SnmpServerHandler() {
				@Override
				public void from(Oid oid, Callback callback) {
					if (oid.raw.length == 4) {
						for (int k = 0; k < 3; k++) {
							Oid o = oid.append(new Oid(new long[] { k }));
							callback.handle(new SnmpResult(o, "val" + o.toString()));
						}
					}
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
			};
			
			int port = 8080;
			final Wait waitServer = new Wait();
			try (Disconnectable snmpServer = ninio.create(SnmpServer.builder().with(UdpSocket.builder().bind(new Address(Address.LOCALHOST, port)))
				.handle(new SnmpServerHandler() {
					@Override
					public void from(Oid oid, Callback callback) {
						handler.from(oid, callback);
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
				try (SnmpConnecter snmpClient = ninio.create(SnmpClient.builder().with(InMemoryCache.<Integer>builder()
						.dataExpiration(4d)
						.requestExpiration(4d)
						.using(new SnmpInMemoryCacheInterpreter())))) {
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
					for (int i = 0; i < 200; i++) {
						Assertions.assertThat(get(snmpClient, new Address(Address.LOCALHOST, port), OID.append(new Oid(new long[] { i }))).toString()).isEqualTo("[1.1.1." + i + ".0:val1.1.1." + i + ".0, 1.1.1." + i + ".1:val1.1.1." + i + ".1, 1.1.1." + i + ".2:val1.1.1." + i + ".2]");
						Thread.sleep(1000);
					}
				}
			}
			waitServer.waitFor();
		}
	}

	private static List<SnmpResult> get(SnmpConnecter snmpClient, Address a, Oid oid) throws IOException {
		final Lock<List<SnmpResult>, IOException> lock = new Lock<>();
		snmpClient.request().community("community").build(a, oid).receive(new SnmpReceiver() {
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

}
