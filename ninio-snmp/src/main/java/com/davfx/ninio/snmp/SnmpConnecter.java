package com.davfx.ninio.snmp;

import java.io.IOException;

import com.davfx.ninio.core.Address;

public interface SnmpConnecter extends AutoCloseable {
	
	interface ConnectCallback {
		void connected(Address address);
		void failed(IOException ioe);
		void closed();
	}
	
	void connect(ConnectCallback callback);

	interface GetCallback {
		void received(SnmpResult result);
		void finished();
		void failed(IOException ioe);
	}
	
	interface Cancelable {
		void cancel();
	}

	Cancelable get(Address address, String community, AuthRemoteSpecification authRemoteSpecification, Oid oid, GetCallback callback);

	void close();
}
