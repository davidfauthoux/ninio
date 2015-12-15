package com.davfx.ninio.proxy;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.util.Wait;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class ProxyServerMain {
	private static final Config CONFIG = ConfigFactory.load();

	private ProxyServerMain() {
	}
	
	public static void main(String[] args) throws Exception {
		Wait wait = new Wait();
		try (Queue queue = new Queue()) {
			try (ProxyServer server = new ProxyServer(queue, CONFIG.getInt("proxy.port"), CONFIG.getInt("proxy.maxSimultaneousClients"))) {
				for (Config c : CONFIG.getConfigList("proxy.forward")) {
					server.override(null, c.getString("type"), new ForwardServerSideConfigurator(queue, new Address(c.getString("host"), c.getInt("port")), null));
				}
				for (String host : CONFIG.getStringList("proxy.filter")) {
					server.filter(host);
				}
				server.start();
				wait.waitFor();
			}
		}
	}
}
