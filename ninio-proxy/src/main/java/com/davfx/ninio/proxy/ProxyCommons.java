package com.davfx.ninio.proxy;

import com.davfx.ninio.proxy.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
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
		
		private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(ProxyCommons.class.getPackage().getName());
		
		public static final String TCP = CONFIG.getString("tcp");
		public static final String SSL = CONFIG.getString("ssl");
		public static final String UDP = CONFIG.getString("udp");
		public static final String TCPDUMP = CONFIG.getString("tcpdump");
		public static final String RAW = CONFIG.getString("raw");
		public static final String WEBSOCKET = CONFIG.getString("websocket");
		public static final String HTTP = CONFIG.getString("http");
		
		private Types() {
		}
	}
}
