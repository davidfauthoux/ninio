package com.davfx.ninio.http.service;

import java.io.File;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.http.HttpListening;
import com.davfx.ninio.http.service.controllers.FileAssets;
import com.davfx.ninio.util.SerialExecutor;
import com.davfx.ninio.util.Wait;

public final class Main {
	private Main() {
	}
	
	public static void main(String[] args) {
		int port = Integer.parseInt(System.getProperty("port"));
		File dir = new File(System.getProperty("directory"));
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable tcp = ninio.create(TcpSocketServer.builder()
					.bind(new Address(Address.ANY, port))
					.listening(HttpListening.builder()
							.with(new SerialExecutor(Main.class))
							.with(Annotated.builder(HttpService.builder()).register(new FileAssets(dir, "index.html")).build()).build()))) {
				new Wait().waitFor();
			}
		}
	}
}
