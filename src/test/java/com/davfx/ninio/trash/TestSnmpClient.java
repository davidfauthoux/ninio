package com.davfx.ninio.trash;

import java.io.IOException;

import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.Result;
import com.davfx.ninio.snmp.SnmpClient;
import com.davfx.ninio.snmp.SnmpClientHandler;

public class TestSnmpClient {
	// sudo /usr/bin/snmpconf -i
	//              - all
	//              - 1
	//              - 1
	//              - priv
	//              - finished
	//              - finished
	//              - quit

	// sudo launchctl unload -w /System/Library/LaunchDaemons/org.net-snmp.snmpd.plist
	// sudo net-snmp-config --create-snmpv3-user -a mypassword -x mypassword -X AES -A SHA priv2
	// sudo launchctl load -w /System/Library/LaunchDaemons/org.net-snmp.snmpd.plist
	
	// Useful resources:
	// http://www.mibdepot.com/cgi-bin/getmib3.cgi?win=mib_a&n=SNMP-USER-BASED-SM-MIB&r=cisco&f=SNMP-USM-MIB-V1SMI.my&t=tree&v=v1&i=0
	// http://www.frameip.com/snmp/
	// http://msdn.microsoft.com/en-us/library/windows/desktop/aa378974(v=vs.85).aspx
	
	public static void main(String[] args) {
		int test = 0x7;
		if ((test & 0x1) != 0) {
			new SnmpClient().withCommunity("public").withLoginPassword("priv2", "mypassword", "SHA", "priv2", "mypassword", "AES").connect(new SnmpClientHandler() {
			//new SnmpClient().withCommunity("public").withLoginPassword("priv", "mypassword", "MD5", "priv", "mypassword", "DES").connect(new SnmpClientHandler() {
				
				@Override
				public void failed(IOException e) {
					System.out.println("FAILED");
					System.exit(1);
				}
				
				@Override
				public void close() {
					System.out.println("CLOSED");
					System.exit(1);
				}
				
				@Override
				public void launched(final Callback callback) {
					callback.get(new Oid("1.3.6.1.2.1.1.5.0"), new SnmpClientHandler.Callback.GetCallback() {
						
						@Override
						public void failed(IOException e) {
							System.out.println("# FAILED");
							System.exit(1);
						}
						
						@Override
						public void finished(Result result) {
							new Thread(new Runnable() {
								@Override
								public void run() {
									try {
										Thread.sleep(200000);
									} catch (InterruptedException e1) {
									}
									callback.get(new Oid("1.3.6.1.2.1.1.6.0"), new SnmpClientHandler.Callback.GetCallback() {
										
										@Override
										public void failed(IOException e) {
											System.out.println("# FAILED");
											System.exit(1);
										}
										
										@Override
										public void finished(Result result) {
											System.out.println(result);
										}
										
										@Override
										public void finished(Iterable<Result> results) {
											for (Result result : results) {
												System.out.println(result);
											}						
										}
									});
								}
							}).start();
							System.out.println(result);
						}
						
						@Override
						public void finished(Iterable<Result> results) {
							for (Result result : results) {
								System.out.println(result);
							}						
						}
					});
				}
			});
		}
		if ((test & 0x2) != 0) {
			new SnmpClient().withCommunity("public").withLoginPassword("priv", "mypassword", "MD5", "priv", "mypassword", "DES").connect(new SnmpClientHandler() {
				
				@Override
				public void failed(IOException e) {
					System.out.println("FAILED");
					System.exit(1);
				}
				
				@Override
				public void close() {
					System.out.println("CLOSED");
					System.exit(1);
				}
				
				@Override
				public void launched(Callback callback) {
					callback.get(new Oid("1.3.6.1.2.1.1.5.0"), new SnmpClientHandler.Callback.GetCallback() {
						
						@Override
						public void failed(IOException e) {
							System.out.println("# FAILED");
							System.exit(1);
						}
						
						@Override
						public void finished(Result result) {
							System.out.println(result);
						}
						
						@Override
						public void finished(Iterable<Result> results) {
							for (Result result : results) {
								System.out.println(result);
							}						
						}
					});
				}
			});
		}
		if ((test & 0x4) != 0) {
			new SnmpClient().withCommunity("public").connect(new SnmpClientHandler() {
				
				@Override
				public void failed(IOException e) {
					System.out.println("FAILED");
					System.exit(1);
				}
				
				@Override
				public void close() {
					System.out.println("CLOSED");
					System.exit(1);
				}
				
				@Override
				public void launched(Callback callback) {
					callback.get(new Oid("1.3.6.1.2.1.1.4.0"), new SnmpClientHandler.Callback.GetCallback() {
						
						@Override
						public void failed(IOException e) {
							System.out.println("# FAILED");
							System.exit(1);
						}
						
						@Override
						public void finished(Result result) {
							System.out.println(result);
						}
						
						@Override
						public void finished(Iterable<Result> results) {
							for (Result result : results) {
								System.out.println(result);
							}						
						}
					});
				}
			});
		}
	}
}
