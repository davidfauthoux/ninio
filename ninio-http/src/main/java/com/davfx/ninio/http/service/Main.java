package com.davfx.ninio.http.service;

import java.io.File;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Listener;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.http.HttpListening;
import com.davfx.ninio.http.dependencies.Dependencies;
import com.davfx.ninio.http.service.controllers.FileAssets;
import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.SerialExecutor;
import com.davfx.ninio.util.Wait;
import com.typesafe.config.Config;

public final class Main {
	private Main() {
	}
	
	private static Config CONFIG = ConfigUtils.load(new Dependencies(), "run");
	
	public static void main(String[] args) {
		int port = CONFIG.getInt("port");
		File dir = new File(CONFIG.getString("directory"));
		try (Ninio ninio = Ninio.create()) {
			try (Listener tcp = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)))) {
				tcp.listen(HttpListening.builder()
							.with(new SerialExecutor(Main.class))
							.with(Annotated.builder(HttpService.builder()).register(null, new FileAssets(dir, "index.html")).build()).build());
				new Wait().waitFor();
			}
		}
	}
}
