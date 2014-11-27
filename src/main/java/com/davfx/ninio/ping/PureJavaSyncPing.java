package com.davfx.ninio.ping;

import java.io.IOException;
import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PureJavaSyncPing implements SyncPing {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PureJavaSyncPing.class);
	
	public PureJavaSyncPing() {
	}
	
	@Override
	public boolean isReachable(String host, double timeout) {
		InetAddress toReach;
		try {
			toReach = InetAddress.getByName(host);
		} catch (IOException ioe) {
			return false;
		}
		try {
			return toReach.isReachable((int) (timeout * 1000d));
		} catch (IOException ioe) {
			LOGGER.debug("Unreachable", ioe);
			return false;
		}
	}
}
