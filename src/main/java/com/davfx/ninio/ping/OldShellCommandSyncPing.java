package com.davfx.ninio.ping;

import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public class OldShellCommandSyncPing implements OldSyncPing {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(OldShellCommandSyncPing.class);
	
	public OldShellCommandSyncPing() {
	}
	
	@Override
	public boolean isReachable(byte[] address, double timeout) {
		try {
			ProcessBuilder b = new ProcessBuilder("ping",
					"-c", String.valueOf(1),
					"-W", String.valueOf((int) timeout
				), InetAddress.getByAddress(address).getHostAddress());
			Process p = b.start();
			return (p.waitFor() == 0);
		} catch (Exception e) {
			LOGGER.error("Could not ping", e);
			return false;
		}
	}
	
	public static void main(String[] args) {
		System.out.println(new OldShellCommandSyncPing().isReachable(new byte[] { 8, 8, 8, 88 }, 1d));
	}
}
