package com.davfx.ninio.snmp;

import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Failable;

public interface SnmpClientHandler extends Closeable, Failable {
	interface Callback extends Closeable {
		interface GetCallback extends Failable {
			void finished(Iterable<Result> results);
			void finished(Result result);
		}
		void get(Oid oid, GetCallback callback);
	}
	void launched(Callback callback);
}
