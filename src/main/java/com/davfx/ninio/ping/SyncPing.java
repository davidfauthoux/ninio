package com.davfx.ninio.ping;


interface SyncPing {
	boolean isReachable(byte[] address, double timeout);
}
