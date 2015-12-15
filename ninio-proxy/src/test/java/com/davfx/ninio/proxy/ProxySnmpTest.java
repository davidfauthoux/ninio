package com.davfx.ninio.proxy;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.DatagramReady;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.Result;
import com.davfx.ninio.snmp.Snmp;
import com.davfx.ninio.snmp.SnmpClientHandler;
import com.davfx.ninio.snmp.SnmpServer;
import com.davfx.ninio.snmp.SnmpServerUtils;
import com.davfx.util.Lock;

public class ProxySnmpTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProxySnmpTest.class);
	
	@Test
	public void testDatagram() throws Exception {
		int proxyServerPort = 8161;
		
		TreeMap<Oid, String> map = new TreeMap<>();
		map.put(new Oid("1.1.1"), "val1.1.1");
		map.put(new Oid("1.1.1.1"), "val1.1.1.1");
		map.put(new Oid("1.1.1.2"), "val1.1.1.2");
		map.put(new Oid("1.1.2"), "val1.1.2");
		map.put(new Oid("1.1.3.1"), "val1.1.3.1");
		map.put(new Oid("1.1.3.2"), "val1.1.3.2");
		
		try (Queue queue = new Queue()) {
			try (SnmpServer snmpServer = new SnmpServer(queue, new DatagramReady(queue.getSelector(), queue.allocator()).bind(), new Address(Address.LOCALHOST, proxyServerPort), SnmpServerUtils.from(map))) {
				queue.finish().waitFor();

				try (ProxyServer proxyServer = new ProxyServer(queue, proxyServerPort, 1)) {
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

						{
							final Lock<List<Result>, IOException> lock = new Lock<>();
							new Snmp().override(proxyClient.datagram(queue)).to(new Address(Address.LOCALHOST, proxyServerPort)).get(new Oid("1.1.1"), new SnmpClientHandler.Callback.GetCallback() {
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
						}
						{
							final Lock<List<Result>, IOException> lock = new Lock<>();
							new Snmp().override(proxyClient.datagram(queue)).to(new Address(Address.LOCALHOST, proxyServerPort)).get(new Oid("1.1.1"), new SnmpClientHandler.Callback.GetCallback() {
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
						}
					}
				}
				queue.finish().waitFor();
			}
		}
	}
	
}
