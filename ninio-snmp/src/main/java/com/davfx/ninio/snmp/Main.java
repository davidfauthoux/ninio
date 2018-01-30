package com.davfx.ninio.snmp;

import java.io.IOException;
import java.net.InetAddress;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.UdpSocket;
import com.davfx.ninio.util.Wait;

public class Main {
	public static void main(String[] args) throws Exception {
		String community = System.getProperty("community");
		System.out.println("community = " + community);
		String login = System.getProperty("login", "admin");
		System.out.println("login = " + login);
		String authPassword = System.getProperty("authPassword", null);
		System.out.println("authPassword = " + authPassword);
		String privPassword = System.getProperty("privPassword", null);
		System.out.println("privPassword = " + privPassword);
		String authMethod = System.getProperty("authMethod", "SHA");
		System.out.println("authMethod = " + authMethod);
		String privMethod = System.getProperty("privMethod", "AES");
		System.out.println("privMethod = " + privMethod);
		String contextName = System.getProperty("contextName", null);
		System.out.println("contextName = " + contextName);
		byte[] ip = InetAddress.getByName(System.getProperty("ip", "localhost")).getAddress();
		String portProperty = System.getProperty("port");
		int port = (portProperty == null) ? SnmpClient.DEFAULT_PORT : Integer.parseInt(portProperty);
		Oid oid = new Oid(System.getProperty("oid", "1.3.6.1.2.1.2.2.1.2.1"));

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
