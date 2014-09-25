package com.davfx.ninio.trash;

import java.io.IOException;

import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.Result;
import com.davfx.ninio.snmp.SnmpClient;
import com.davfx.ninio.snmp.SnmpClientHandler;

public final class Snmp {
	public static void main(String[] args) {
		final Oid oid = new Oid(System.getProperty("oid"));
		final int n = Integer.parseInt(System.getProperty("n", "1"));
		new SnmpClient().withHost(System.getProperty("host")).withCommunity(System.getProperty("community")).connect(new SnmpClientHandler() {
			
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
				for (int i = 0; i < n; i++) {
					final int j = i;
					callback.get(oid, new SnmpClientHandler.Callback.GetCallback() {
						
						@Override
						public void failed(IOException e) {
							System.out.println("#" + j + " " + " FAILED");
							// System.exit(1);
						}
						
						@Override
						public void finished(Result result) {
							System.out.println("#" + j + " " + result);
							// System.exit(0);
						}
						
						@Override
						public void finished(Iterable<Result> results) {
							System.out.println("#" + j + " --");
							for (Result result : results) {
								System.out.println("#" + j + " " + result);
							}						
							System.out.println("#" + j + " --");
							// System.exit(0);
						}
					});
				}
			}
		});
	}
}
