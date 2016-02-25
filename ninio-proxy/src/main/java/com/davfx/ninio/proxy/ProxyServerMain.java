package com.davfx.ninio.proxy;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.util.Wait;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class ProxyServerMain {
	
	private static final Config CONFIG = ConfigFactory.load(ProxyServerMain.class.getClassLoader());

	private ProxyServerMain() {
	}
	
	public static void main(String[] args) throws Exception {
		Wait wait = new Wait();
		try (Queue queue = new Queue()) {
			try (ProxyServer server = new ProxyServer(queue, new Address(CONFIG.getString("ninio.proxy.host"), CONFIG.getInt("ninio.proxy.port")), CONFIG.getInt("ninio.proxy.maxSimultaneousClients"))) {
				for (Config c : CONFIG.getConfigList("ninio.proxy.forward")) {
					server.override(c.getString("type"), new ForwardServerSideConfigurator(queue, new Address(c.getString("host"), c.getInt("port")), null));
				}
				for (Config c : CONFIG.getConfigList("ninio.proxy.filter")) {
					server.filter(new Address(c.getString("host"), c.getInt("port")));
				}
				server.start();
				wait.waitFor();
			}
		}
	}
}
