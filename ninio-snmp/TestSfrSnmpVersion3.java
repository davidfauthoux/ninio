package com.davfx.ninio.snmp;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.core.TcpdumpSyncDatagramReady;
import com.davfx.ninio.core.TcpdumpSyncDatagramReadyFactory;
import com.davfx.ninio.proxy.BaseServerSide;
import com.davfx.ninio.proxy.ProxyClient;
import com.davfx.ninio.proxy.ProxyListener;
import com.davfx.ninio.proxy.ProxyServer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class TestSfrSnmpVersion3 {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestSfrSnmpVersion3.class);
	private static final Config CONFIG = ConfigFactory.load(BaseServerSide.class.getClassLoader());

	public static void main(String[] args) throws Exception {
		
		final Address proxyAddress = new Address("127.0.0.1", 9999);

		try (Queue queue = new Queue()) {

			try (ProxyServer proxyServer = new ProxyServer(queue, proxyAddress, 10)) {
				proxyServer.start();
	
				try (ProxyClient proxyClient = new ProxyClient(proxyAddress, new ProxyListener() {
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Proxy failed: {}", proxyAddress, e);
					}
					@Override
					public void disconnected() {
						LOGGER.debug("Proxy disconnected: {}", proxyAddress);
					}
					@Override
					public void connected() {
						LOGGER.debug("Proxy connected: {}", proxyAddress);
					}
				})) {
				
					String TCPDUMP_HOST = CONFIG.getString("ninio.proxy.tcpdump.host");
					int TCPDUMP_PORT = CONFIG.getInt("ninio.proxy.tcpdump.port");
					String TCPDUMP_INTERFACE = CONFIG.getString("ninio.proxy.tcpdump.interface");

					TcpdumpSyncDatagramReady.Rule rule = new TcpdumpSyncDatagramReady.SourcePortRule(TCPDUMP_PORT);
					try (TcpdumpSyncDatagramReady.Receiver receiver = new TcpdumpSyncDatagramReady.Receiver(rule, TCPDUMP_INTERFACE, new Address(TCPDUMP_HOST, 0))) {
						ReadyFactory snmpReadyFactory;
						// snmpReadyFactory = new DatagramReadyFactory(queue);
						// snmpReadyFactory = proxyClient.datagram(queue);
						snmpReadyFactory = new TcpdumpSyncDatagramReadyFactory(queue, receiver);
	
						try (SnmpClient client = new SnmpClient(queue, snmpReadyFactory)) {
							client.connect(new SnmpClientHandler() {
								@Override
								public void failed(IOException e) {
									LOGGER.error("Failed", e);
								}
								@Override
								public void close() {
									LOGGER.error("Client closed");
								}
								
								@Override
								public void launched(final Callback callback) {
									Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(new Runnable() {
										@Override
										public void run() {
											String[] hosts = new String[] {
												"10.66.2.13",
												"10.66.2.14",
												"10.66.2.11",
												"10.66.2.12"
											};
											String[] oids = new String[] {
												"1.3.6.1.4.1.9.9.109.1.1.1.1.6",
												"1.3.6.1.4.1.9.9.109.1.1.1.1.7",
												"1.3.6.1.4.1.9.9.109.1.1.1.1.8",
												"1.3.6.1.4.1.9.9.109.1.1.1.1.9",
												"1.3.6.1.4.1.9.9.109.1.1.1.1.10",
												"1.3.6.1.4.1.9.9.109.1.1.1.1.12",
												"1.3.6.1.4.1.9.9.109.1.1.1.1.13",
												
												"1.3.6.1.4.1.9.9.276.1.1.4.1.1",
												"1.3.6.1.4.1.9.9.276.1.1.4.1.2",
												"1.3.6.1.4.1.9.9.276.1.1.4.1.3",
												"1.3.6.1.4.1.9.9.276.1.1.4.1.4",
												"1.3.6.1.4.1.9.9.276.1.1.4.1.5",
												"1.3.6.1.4.1.9.9.276.1.1.4.1.6",
												"1.3.6.1.4.1.9.9.276.1.1.4.1.7",
												"1.3.6.1.4.1.9.9.276.1.1.4.1.8",
												"1.3.6.1.2.1.2.2.1.2",
												"1.3.6.1.2.1.2.2.1.7",
												"1.3.6.1.2.1.2.2.1.8",
												"1.3.6.1.2.1.2.2.1.12",
												"1.3.6.1.2.1.2.2.1.13",
												"1.3.6.1.2.1.2.2.1.14",
												"1.3.6.1.2.1.2.2.1.15",
												"1.3.6.1.2.1.2.2.1.18",
												"1.3.6.1.2.1.2.2.1.19",
												"1.3.6.1.2.1.2.2.1.20",
												"1.3.6.1.2.1.2.2.1.21",
												"1.3.6.1.2.1.31.1.1.1.2",
												"1.3.6.1.2.1.31.1.1.1.3",
												"1.3.6.1.2.1.31.1.1.1.4",
												"1.3.6.1.2.1.31.1.1.1.5",
												"1.3.6.1.2.1.31.1.1.1.6",
												"1.3.6.1.2.1.31.1.1.1.7",
												"1.3.6.1.2.1.31.1.1.1.8",
												"1.3.6.1.2.1.31.1.1.1.9",
												"1.3.6.1.2.1.31.1.1.1.10",
												"1.3.6.1.2.1.31.1.1.1.11",
												"1.3.6.1.2.1.31.1.1.1.12",
												"1.3.6.1.2.1.31.1.1.1.13",
												"1.3.6.1.2.1.31.1.1.1.15"
											};
											for (String h : hosts) {
												for (String oid : oids) {
													callback.get(new Address(h, Snmp.DEFAULT_PORT), null, new AuthRemoteSpecification("user_bilo", "ACI-Bilo123-phrase", "SHA", "", "ACI-Bilo123", "AES"), 100d, new Oid(oid), new SnmpClientHandler.Callback.GetCallback() {
														@Override
														public void failed(IOException e) {
															LOGGER.error("Get failed", e);
														}
						
														private final List<Result> r = new LinkedList<>();
														
														@Override
														public void close() {
															if (r.isEmpty()) {
																LOGGER.error("Get empty");
															} else {
																LOGGER.info("ok");
															}
														}
														
														@Override
														public void result(Result result) {
															r.add(result);
														}
													});
												}
											}
										}
									}, 0, 5, TimeUnit.MINUTES);
								}
							});
							Thread.sleep(300000);
						}
					}
				}
			}
		}
	}
}

