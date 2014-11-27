package com.davfx.ninio.ping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShellCommandSyncPing implements SyncPing {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ShellCommandSyncPing.class);
	
	public ShellCommandSyncPing() {
	}
	
	@Override
	public boolean isReachable(String host, double timeout) {
		try {
			ProcessBuilder b = new ProcessBuilder("ping",
					"-c", String.valueOf(1),
					"-W", String.valueOf((int) timeout
				), host);
			Process p = b.start();
			return (p.waitFor() == 0);
		} catch (Exception e) {
			LOGGER.error("Could not ping", e);
			return false;
		}
	}
	
	public static void main(String[] args) {
		System.out.println(new ShellCommandSyncPing().isReachable("8.8.8.8", 1d));
	}
}
