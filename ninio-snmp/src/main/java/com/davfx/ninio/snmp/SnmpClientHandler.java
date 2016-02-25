package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.Failable;

public interface SnmpClientHandler extends Closeable, Failable {
	interface Callback extends Closeable {
		interface GetCallback extends Closeable, Failable {
			void result(Result result);
		}
		void get(Address address, String community, AuthRemoteEngine authEngine, double timeoutFromBeginning, Oid oid, GetCallback callback);
	}
	void launched(Callback callback);
}
