package com.davfx.ninio.proxy.v3;

import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

interface ProxyCommons {
	interface Commands {
		int SEND_WITH_ADDRESS = 0;
		int SEND_WITHOUT_ADDRESS = 1;
		int CLOSE = 2;
		int CONNECT_WITH_ADDRESS = 3;
		int CONNECT_WITHOUT_ADDRESS = 4;
	}
	
	final class Types {
		
		private static final Config CONFIG = ConfigUtils.load(ProxyCommons.class);
		
		public static final String TCP = CONFIG.getString("ninio.proxy.tcp");
		public static final String SSL = CONFIG.getString("ninio.proxy.ssl");
		public static final String UDP = CONFIG.getString("ninio.proxy.udp");
		public static final String RAW = CONFIG.getString("ninio.proxy.raw");
		public static final String WEBSOCKET = CONFIG.getString("ninio.proxy.websocket");
		public static final String HTTP = CONFIG.getString("ninio.proxy.http");
		
		private Types() {
		}
	}
}
