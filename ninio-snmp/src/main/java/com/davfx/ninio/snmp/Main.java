package com.davfx.ninio.snmp;

import java.io.IOException;
import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.UdpSocket;
import com.davfx.ninio.util.Wait;

public class Main {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

	//snmpwalk -v3  -l authPriv -u snmp-poller -a SHA -A "PASSWORD1"  -x AES -X "PASSWORD1" 10.3.77.120 1.3.6.1.2.1.2.2.1.2
	//snmpwalk -v3  -l authPriv -u gbitot -a SHA -A "totototo"  -x AES -X "tatatata" 10.3.101.6 1.3.6.1.2.1.2.2.1.2 
	public static void main(String[] args) throws Exception {
		LOGGER.trace("TRACE");
		String community = System.getProperty("community"); //, "public");
		System.out.println("community = " + community);
		String login = System.getProperty("login", "snmp-poller");
		System.out.println("login = " + login);
		String authPassword = System.getProperty("authPassword", "PASSWORD1");
		System.out.println("authPassword = " + authPassword);
		String privPassword = System.getProperty("privPassword", "PASSWORD1");
		System.out.println("privPassword = " + privPassword);
		String authMethod = System.getProperty("authMethod", "SHA");
		System.out.println("authMethod = " + authMethod);
		String privMethod = System.getProperty("privMethod", "AES");
		System.out.println("privMethod = " + privMethod);
		String contextName = System.getProperty("contextName", null);
		System.out.println("contextName = " + contextName);
		byte[] ip = InetAddress.getByName(System.getProperty("ip", "10.3.101.6")).getAddress();
		String portProperty = System.getProperty("port");
		int port = (portProperty == null) ? SnmpClient.DEFAULT_PORT : Integer.parseInt(portProperty);
		Oid oid = new Oid(System.getProperty("oid", "1.3.6.1.2.1.2.2.1.2"));

		System.out.println("ip = " + Address.ipToString(ip));
		System.out.println("port = " + port);
		System.out.println("oid = " + oid);

		try (Ninio ninio = Ninio.create()) {
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
				
				SnmpRequestBuilder r = snmpClient.request();
				if (community == null) {
					r.auth(new AuthRemoteSpecification(login, authPassword, authMethod, privPassword, privMethod, contextName));
				} else {
					r.community(community);
				}
				r.build(new Address(ip, port), oid).call(SnmpCallType.GET, new SnmpReceiver() {
					@Override
					public void received(SnmpResult result) {
						System.out.println(result);
					}
					@Override
					public void finished() {
						System.out.println("---FINISHED---");
						waitClient.run();
					}
					@Override
					public void failed(IOException ioe) {
						ioe.printStackTrace();
					}
				});
				waitClient.waitFor();
			}
		}
	}
}
