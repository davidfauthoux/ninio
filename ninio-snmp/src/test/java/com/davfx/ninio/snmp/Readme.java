package com.davfx.ninio.snmp;

import java.io.IOException;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.snmp.SnmpClientHandler.Callback.GetCallback;

public final class Readme {
	public static void main(String[] args) throws Exception {
		new Snmp().to(new Address("127.0.0.1", Snmp.DEFAULT_PORT)).get(new Oid("1.3.6.1.2.1.1.4.0"), new GetCallback() {
			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
			@Override
			public void close() {
				System.out.println("Done");
			}
			@Override
			public void result(Result result) {
				System.out.println("Result: " + result);
			}
		});
		
		Thread.sleep(1000);
	}
}
