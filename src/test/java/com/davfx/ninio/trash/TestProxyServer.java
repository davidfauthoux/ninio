package com.davfx.ninio.trash;

import com.davfx.ninio.proxy.ProxyServer;

public class TestProxyServer {
	public static void main(String[] args) throws Exception {
		new ProxyServer(9993, 10).start();;
	}
}
