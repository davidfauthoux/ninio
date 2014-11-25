package com.davfx.ninio.trash;

import com.davfx.ninio.proxy.ProxyServer;

public class TestScriptProxy2 {
	public static void main(String[] args) throws Exception {
		new ProxyServer(6666, 2).start();
	}

}
