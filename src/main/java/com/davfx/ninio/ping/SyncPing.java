package com.davfx.ninio.ping;

interface SyncPing {
	boolean isReachable(String host, double timeout);
}
