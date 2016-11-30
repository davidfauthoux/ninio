package com.davfx.ninio.snmp;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.UdpSocket;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;

public class SnmpTrapTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpTrapTest.class);
	
	@Test
	public void test() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			int port = 8080;
			
			final Oid sentOid = new Oid("1.1.1.1.1.9");
			
			final Lock<SnmpResult, IOException> result = new Lock<>();
			try (Connecter snmpServer = ninio.create(UdpSocket.builder().bind(new Address(Address.LOCALHOST, port)))) {
				snmpServer.connect(new Connection() {
					@Override
					public void received(Address address, ByteBuffer buffer) {
						int requestId;
						String community;
						int request;
						try {
							BerReader ber = new BerReader(buffer);
							ber.beginReadSequence();
							{
								ber.readInteger(); // Version
								community = BerPacketUtils.string(ber.readBytes());
								LOGGER.debug("community = {}", community);
								request = ber.beginReadSequence();
								{
									requestId = ber.readInteger();
									ber.readInteger(); // Non-repeater, not used
									ber.readInteger(); // bulkLength, ignored
									ber.beginReadSequence();
									while (ber.hasRemainingInSequence()) {
										ber.beginReadSequence();
										{
											Oid oid = ber.readOid();
											String value = ber.readValue();
											LOGGER.debug("oid = {}", oid);
											LOGGER.debug("value = {}", value);
											if (oid.equals(sentOid)) {
												result.set(new SnmpResult(oid, value));
											}
										}
										ber.endReadSequence();
									}
									ber.endReadSequence();
								}
								ber.endReadSequence();
							}
							ber.endReadSequence();
						} catch (IOException e) {
							LOGGER.error("Invalid packet", e);
							return;
						}
						LOGGER.debug("requestId = {}", requestId);
						LOGGER.debug("community = {}", community);
						LOGGER.debug("request = {} (should be {})", request, BerConstants.TRAP);
					}
					@Override
					public void connected(Address address) {
					}
					@Override
					public void closed() {
					}
					@Override
					public void failed(IOException e) {
					}
				});
				
				final Wait waitClient = new Wait();
				try (SnmpConnecter snmpClient = ninio.create(SnmpClient.builder().with(UdpSocket.builder()))) {
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
					
					String sentValue = "trap-test";
					snmpClient.request().community("community").build(new Address(Address.LOCALHOST, port), new Oid("1.1.1")).add(sentOid, sentValue).call(SnmpCallType.TRAP, null);
					Assertions.assertThat(result.waitFor()).isEqualTo(new SnmpResult(sentOid, sentValue));
				}
				waitClient.waitFor();
			}
		}
	}
	
	public static void main(String[] args) {
		// sudo snmptrapd -f -Le -d
		try (Ninio ninio = Ninio.create()) {
			try (SnmpConnecter snmpClient = ninio.create(SnmpClient.builder().with(UdpSocket.builder()))) {
				snmpClient.connect(null);
				snmpClient.request().community("private").build(new Address(Address.LOCALHOST, SnmpClient.DEFAULT_TRAP_PORT), new Oid("1.1.1.1.1")).add(new Oid("1.1.1.1.1.10"), "trap-test").call(SnmpCallType.TRAP, null);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			}
		}
	}
}
