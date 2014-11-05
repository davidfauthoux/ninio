package com.davfx.ninio.snmp;

import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Failable;

public interface SnmpClientHandler extends Closeable, Failable {
	interface Callback extends Closeable {
		interface GetCallback extends Closeable, Failable {
			void result(Result result);
		}
		void get(Oid oid, GetCallback callback);
	}
	void launched(Callback callback);
}
