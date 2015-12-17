package com.davfx.ninio.proxy;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.snmp.InternalSnmpMemoryCacheServerReadyFactory;
import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.Result;
import com.davfx.ninio.snmp.Snmp;
import com.davfx.ninio.snmp.SnmpClientHandler;
import com.davfx.util.Lock;

// $> sudo nano /etc/snmp/snmpd.conf
// Uncomment #rwcommunity private
// $> sudo launchctl unload -w /System/Library/LaunchDaemons/org.net-snmp.snmpd.plist
// $> sudo launchctl load -w /System/Library/LaunchDaemons/org.net-snmp.snmpd.plist
@Ignore
public class ProxySnmpOnLocalhostTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProxySnmpOnLocalhostTest.class);
	
	@Test
	public void test() throws Exception {
		int snmpServerPort = 161;
		int proxyServerPort = 8161;
		
		try (Queue queue = new Queue()) {
			try (ProxyServer proxyServer = new ProxyServer(queue, new Address(Address.ANY, proxyServerPort), 1)) {
				try (InternalSnmpMemoryCacheServerReadyFactory i = new InternalSnmpMemoryCacheServerReadyFactory(new InternalSnmpMemoryCacheServerReadyFactory.Filter() {
					@Override
					public boolean cache(Address address, Oid oid) {
						return true;
					}
				}, queue, proxyServer.datagramReadyFactory())) {
					proxyServer.override("_snmp", new SimpleServerSideConfigurator(i));
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
						proxyClient.override("_snmp", new EmptyClientSideConfiguration());

						{
							final Lock<List<Result>, IOException> lock = new Lock<>();
							new Snmp().withCommunity("private").override(proxyClient.of(queue, "_snmp")).to(new Address(Address.LOCALHOST, snmpServerPort)).get(new Oid("1.3.6.1.2.1.1.4"), new SnmpClientHandler.Callback.GetCallback() {
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
							
							Assertions.assertThat(lock.waitFor().toString()).isEqualTo("[1.3.6.1.2.1.1.4.0:Administrator <postmaster@example.com>]");
						}
						LOGGER.debug("Ok then");
						{
							final Lock<List<Result>, IOException> lock = new Lock<>();
							new Snmp().withCommunity("private").override(proxyClient.of(queue, "_snmp")).to(new Address(Address.LOCALHOST, snmpServerPort)).get(new Oid("1.3.6.1.2.1.1.4"), new SnmpClientHandler.Callback.GetCallback() {
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
							
							Assertions.assertThat(lock.waitFor().toString()).isEqualTo("[1.3.6.1.2.1.1.4.0:Administrator <postmaster@example.com>]");
						}
					}
				}
				queue.finish().waitFor();
			}
		}
	}
	
}
