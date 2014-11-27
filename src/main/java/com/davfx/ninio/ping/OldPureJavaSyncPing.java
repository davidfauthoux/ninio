package com.davfx.ninio.ping;

import java.io.IOException;
import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public class OldPureJavaSyncPing implements OldSyncPing {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(OldPureJavaSyncPing.class);
	
	public OldPureJavaSyncPing() {
	}
	
	@Override
	public boolean isReachable(byte[] address, double timeout) {
		InetAddress toReach;
		try {
			toReach = InetAddress.getByAddress(address);
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
