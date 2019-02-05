package com.davfx.ninio.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Ports {
	private static final Logger LOGGER = LoggerFactory.getLogger(Ports.class);

	private Ports() {
	}
	
	public static int free(byte[] bindIp) {
		try {
			try (ServerSocket ss = new ServerSocket(0, 0, InetAddress.getByAddress(bindIp))) {
				return ss.getLocalPort();
			}
		} catch (IOException ioe) {
			LOGGER.error("Could not find free port", ioe);
			return 0;
		}
	}
}
