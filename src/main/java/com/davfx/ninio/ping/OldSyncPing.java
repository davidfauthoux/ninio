package com.davfx.ninio.ping;

@Deprecated
interface OldSyncPing {
	boolean isReachable(byte[] address, double timeout);
}
