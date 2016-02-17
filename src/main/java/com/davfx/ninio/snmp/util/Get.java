package com.davfx.ninio.snmp.util;

import java.io.IOException;

import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.Result;
import com.davfx.ninio.snmp.SnmpClient;
import com.davfx.ninio.snmp.SnmpClientConfigurator;
import com.davfx.ninio.snmp.SnmpClientHandler;
import com.davfx.ninio.snmp.SnmpClientHandler.Callback.GetCallback;

// sudo launchctl unload -w /System/Library/LaunchDaemons/org.net-snmp.snmpd.plist
// sudo /usr/bin/snmpconf -i // all // snmpd.conf // Access Control Setup // a SNMPv3 read-only user // lo-user // priv // <ENTER> // finished // finished // quit
// sudo net-snmp-config --create-snmpv3-user -ro -a lo-auth-pass -x lo-priv-pass -X AES -A SHA lo-user
// sudo launchctl load -w /System/Library/LaunchDaemons/org.net-snmp.snmpd.plist
// snmpwalk -v 3 -u lo-user -l authPriv -a SHA -A lo-auth-pass -x AES -X lo-priv-pass -On 127.0.0.1 1.3.6.1.2.1.1.4
public final class Get {
	public static void main(String[] args) throws Exception {
		/*
		System.setProperty("authLogin", "lo-user");
		System.setProperty("authPassword", "lo-auth-pass");
		System.setProperty("authDigestAlgorithm", "SHA");
		System.setProperty("privLogin", "lo-user");
		System.setProperty("privPassword", "lo-priv-pass");
		System.setProperty("privEncryptionAlgorithm", "AES");
		System.setProperty("host", "172.17.10.120");
		System.setProperty("oid", "1.3.6.1.2.1.1.5.0");
		*/
		
		SnmpClient snmp = new SnmpClient(new SnmpClientConfigurator()
				.withHost(System.getProperty("host"))
				.withLoginPassword(System.getProperty("authLogin"), System.getProperty("authPassword"), System.getProperty("authDigestAlgorithm"), System.getProperty("privLogin"), System.getProperty("privPassword"), System.getProperty("privEncryptionAlgorithm")));
		snmp.connect(new SnmpClientHandler() {
			
			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
			
			@Override
			public void close() {
				System.out.println("CLOSED");
			}
			
			@Override
			public void launched(final Callback callback) {
				callback.get(new Oid(System.getProperty("oid")), new GetCallback() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
					@Override
					public void close() {
						callback.get(new Oid(System.getProperty("oid")), new GetCallback() {
							@Override
							public void failed(IOException e) {
								e.printStackTrace();
							}
							@Override
							public void close() {
								System.out.println("Done");
								System.exit(0);
							}
							@Override
							public void result(Result result) {
								System.out.println(result);
							}
						});
					}
					@Override
					public void result(Result result) {
						System.out.println(result);
					}
				});
			}
		});

		
		Thread.sleep(100000);
	}
}
