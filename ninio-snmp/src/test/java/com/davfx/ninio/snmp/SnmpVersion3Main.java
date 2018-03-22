package com.davfx.ninio.snmp;

import java.io.IOException;
import java.net.InetAddress;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.UdpSocket;
import com.davfx.ninio.util.Wait;

public final class SnmpVersion3Main {
	public static void main(String[] args) throws Exception {
		byte[] host = InetAddress.getByName(System.getProperty("host")).getAddress();
		int port = Integer.parseInt(System.getProperty("port", "" + SnmpClient.DEFAULT_PORT));
		String oid = System.getProperty("oid");
		
		String community = System.getProperty("community");

		String login = System.getProperty("login");
		String authPassword = System.getProperty("auth.password");
		String authDigestAlgorithm = System.getProperty("auth.method", "MD5");
		String privPassword = System.getProperty("priv.password");
		String privEncryptionAlgorithm = System.getProperty("priv.method", "AES");
		String contextName = System.getProperty("context");
		
		try (Ninio ninio = Ninio.create()) {
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
						}
					});
				
				SnmpRequestBuilder r = snmpClient.request();
				if (community == null) {
					r.auth(new AuthRemoteSpecification(login, authPassword, authDigestAlgorithm, privPassword, privEncryptionAlgorithm, contextName));
				} else {
					r.community(community);
				}
				r.build(new Address(host, port), new Oid(oid)).call(SnmpCallType.GETBULK, new SnmpReceiver() {
					@Override
					public void received(SnmpResult result) {
						System.out.println(result);
					}
					
					@Override
					public void finished() {
						System.out.println("---FINISHED");
						System.exit(0);
					}
					
					@Override
					public void failed(IOException ioe) {
						ioe.printStackTrace(System.out);
						System.exit(1);
					}
				});
				new Wait().waitFor();
			}
		}
	}
}
