package com.davfx.ninio.proxy;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.DatagramReady;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.snmp.InternalSnmpCacheServerReadyFactory;
import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.Result;
import com.davfx.ninio.snmp.Snmp;
import com.davfx.ninio.snmp.SnmpClientHandler;
import com.davfx.ninio.snmp.SnmpServer;
import com.davfx.util.Lock;

public class ProxySnmpTest {

	private static final double EXPIRATION = 2d;
	
	static {
		System.setProperty("ninio.snmp.cache.check", "1 second");
		System.setProperty("ninio.snmp.cache.timeout", "2 seconds");
		System.setProperty("ninio.snmp.cache.repeat", "5 seconds");
		System.setProperty("ninio.snmp.cache.expiration", EXPIRATION + " seconds");
	}
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxySnmpTest.class);
	
	@Test
	public void testDatagram() throws Exception {
		int snmpServerPort = 8080;
		int proxyServerPort = 8161;
		
		try (Queue queue = new Queue()) {
			final String[] diff = new String[] { null };
			try (SnmpServer snmpServer = new SnmpServer(queue, new DatagramReady(queue.getSelector(), queue.allocator()).bind(), new Address(Address.LOCALHOST, snmpServerPort), new SnmpServer.Handler() {
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

				try (ProxyServer proxyServer = new ProxyServer(queue, proxyServerPort, 1)) {
					try (InternalSnmpCacheServerReadyFactory i = new InternalSnmpCacheServerReadyFactory(new InternalSnmpCacheServerReadyFactory.Filter() {
						@Override
						public boolean cache(Address address, Oid oid) {
							return true;
						}
					}, queue, proxyServer.datagramReadyFactory())) {
						proxyServer.override(null, "_snmp", new SimpleServerSideConfigurator(i));
						proxyServer.start();
						queue.finish().waitFor();
			
						try (ProxyClient proxyClient = new ProxyClient(new Address(Address.LOCALHOST, proxyServerPort), new ProxyListener() {
							@Override
							public void failed(IOException e) {
								LOGGER.warn("Proxy failed", e);
							}
							@Override
							public void disconnected() {
								LOGGER.debug("Proxy disconnected");
							}
							@Override
							public void connected() {
								LOGGER.debug("Proxy connected");
							}
						})) {
							proxyClient.override(null, "_snmp", new EmptyClientSideConfiguration());
	
							{
								diff[0] = "A";
								final Lock<List<Result>, IOException> lock = new Lock<>();
								new Snmp().override(proxyClient.of(queue, "_snmp")).to(new Address(Address.LOCALHOST, snmpServerPort)).get(new Oid("1.1.1"), new SnmpClientHandler.Callback.GetCallback() {
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
								
								Assertions.assertThat(lock.waitFor().toString()).isEqualTo("[1.1.1.1:A1, 1.1.1.2:A2, 1.1.1.3:A3]");
							}
							{
								diff[0] = "B";
								final Lock<List<Result>, IOException> lock = new Lock<>();
								new Snmp().override(proxyClient.of(queue, "_snmp")).to(new Address(Address.LOCALHOST, snmpServerPort)).get(new Oid("1.1.1"), new SnmpClientHandler.Callback.GetCallback() {
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
								
								Assertions.assertThat(lock.waitFor().toString()).isEqualTo("[1.1.1.1:A1, 1.1.1.2:A2, 1.1.1.3:A3]");
							}
							
							Thread.sleep((long) (EXPIRATION * 1000d * 0.9d));
							
							{
								diff[0] = "C";
								final Lock<List<Result>, IOException> lock = new Lock<>();
								new Snmp().override(proxyClient.of(queue, "_snmp")).to(new Address(Address.LOCALHOST, snmpServerPort)).get(new Oid("1.1.1"), new SnmpClientHandler.Callback.GetCallback() {
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
								
								Assertions.assertThat(lock.waitFor().toString()).isEqualTo("[1.1.1.1:A1, 1.1.1.2:A2, 1.1.1.3:A3]");
							}
							
							Thread.sleep((long) (EXPIRATION * 1000d * 1.1d));
							
							{
								diff[0] = "D";
								final Lock<List<Result>, IOException> lock = new Lock<>();
								new Snmp().override(proxyClient.of(queue, "_snmp")).to(new Address(Address.LOCALHOST, snmpServerPort)).get(new Oid("1.1.1"), new SnmpClientHandler.Callback.GetCallback() {
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
								
								Assertions.assertThat(lock.waitFor().toString()).isEqualTo("[1.1.1.1:D1, 1.1.1.2:D2, 1.1.1.3:D3]");
							}
						}
					}
				}
				queue.finish().waitFor();
			}
		}
	}
	
	@Test
	public void testTimeout() throws Exception {
		Address timeoutAddress = new Address("128.0.0.1", 161);
		int proxyServerPort = 8161;
		
		try (Queue queue = new Queue()) {
			try (ProxyServer proxyServer = new ProxyServer(queue, proxyServerPort, 1)) {
				try (InternalSnmpCacheServerReadyFactory i = new InternalSnmpCacheServerReadyFactory(new InternalSnmpCacheServerReadyFactory.Filter() {
					@Override
					public boolean cache(Address address, Oid oid) {
						return true;
					}
				}, queue, proxyServer.datagramReadyFactory())) {
					proxyServer.override(null, "_snmp", new SimpleServerSideConfigurator(i));
					proxyServer.start();
					queue.finish().waitFor();
		
					try (ProxyClient proxyClient = new ProxyClient(new Address(Address.LOCALHOST, proxyServerPort), new ProxyListener() {
						@Override
						public void failed(IOException e) {
							LOGGER.warn("Proxy failed", e);
						}
						@Override
						public void disconnected() {
							LOGGER.debug("Proxy disconnected");
						}
						@Override
						public void connected() {
							LOGGER.debug("Proxy connected");
						}
					})) {
						proxyClient.override(null, "_snmp", new EmptyClientSideConfiguration());
	
						{
							final Lock<List<Result>, IOException> lock = new Lock<>();
							new Snmp().override(proxyClient.of(queue, "_snmp")).to(timeoutAddress).get(new Oid("1.1.1"), new SnmpClientHandler.Callback.GetCallback() {
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
							
							try {
								lock.waitFor();
								Assertions.fail("Should throw Timeout");
							} catch (IOException ioe) {
								Assertions.assertThat(ioe.getMessage()).isEqualTo("Timeout");
							}
						}
	
						{
							final Lock<List<Result>, IOException> lock = new Lock<>();
							new Snmp().override(proxyClient.of(queue, "_snmp")).to(timeoutAddress).get(new Oid("1.1.1"), new SnmpClientHandler.Callback.GetCallback() {
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
							
							try {
								lock.waitFor();
								Assertions.fail("Should throw Timeout");
							} catch (IOException ioe) {
								Assertions.assertThat(ioe.getMessage()).isEqualTo("Timeout");
							}
						}
					}
				}
				queue.finish().waitFor();
			}
		}
	}
	
}
