package com.davfx.ninio.snmp;

import java.io.IOException;

import com.davfx.ninio.core.Address;

public interface SnmpConnecter {
	interface Connecting extends AutoCloseable {
		void close();

		interface Callback {
			void received(SnmpResult result);
			void finished();
			void failed(IOException ioe);
		}
		
		interface Cancelable {
			void cancel();
		}

		Cancelable get(Address address, String community, AuthRemoteSpecification authRemoteSpecification, Oid oid, Callback callback);
	}
	
	interface Callback {
		void connected(Address address);
		void failed(IOException ioe);
		void closed();
	}
	
	Connecting connect(Callback callback);
}
