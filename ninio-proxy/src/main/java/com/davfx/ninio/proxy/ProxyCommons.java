package com.davfx.ninio.proxy;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

interface ProxyCommons {
	interface Commands {
		int ESTABLISH_CONNECTION = 1;
		int FAIL_CONNECTION = 2;
	}
	
	final class Types {
		
		private static final Config CONFIG = ConfigFactory.load(ProxyCommons.class.getClassLoader());
		
		public static final String SOCKET = CONFIG.getString("ninio.proxy.socket");
		public static final String DATAGRAM = CONFIG.getString("ninio.proxy.datagram");
		public static final String PING = CONFIG.getString("ninio.proxy.ping");
		public static final String HOP = CONFIG.getString("ninio.proxy.hop");
		
		private Types() {
		}
	}
}
