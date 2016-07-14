package com.davfx.ninio.http.service;

import java.io.File;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Listener;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.http.HttpListening;
import com.davfx.ninio.http.service.controllers.FileAssets;
import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.SerialExecutor;
import com.davfx.ninio.util.Wait;
import com.typesafe.config.Config;

public final class Main {
	private Main() {
	}
	
	public static void main(String[] args) {
		Config config = ConfigUtils.load(Main.class, "run");
		int port = config.getInt("port");
		File dir = new File(config.getString("directory"));
		try (Ninio ninio = Ninio.create()) {
			try (Listener.Listening tcp = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)))
					.listen(HttpListening.builder()
							.with(new SerialExecutor(Main.class))
							.with(Annotated.builder(HttpService.builder()).register(new FileAssets(dir, "index.html")).build()).build())) {
				new Wait().waitFor();
			}
		}
	}
}
