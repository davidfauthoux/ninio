package com.davfx.ninio.http.util;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.http.HttpServer;
import com.davfx.ninio.http.HttpServerConfigurator;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;
import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class FileHttpServer {
	private static final Config CONFIG = ConfigUtils.load(RouteHttpServer.class);

	public static void main(String[] args) throws Exception {
		final File root = new File(CONFIG.getString("http.file.path"));
		final String index = CONFIG.getString("http.file.index");
		final Map<String, String> mime = new HashMap<>();
		for (Config c : CONFIG.getConfigList("http.file.mime")) {
			String type = c.getString("type");
			String extension = c.getString("extension");
			mime.put(extension, type);
		}

		Queue queue = new Queue();
		new HttpServer(new HttpServerConfigurator(queue).withAddress(new Address(CONFIG.getString("http.file.bind.host"), CONFIG.getInt("http.file.bind.port"))), new HttpServerHandlerFactory() {
			@Override
			public void failed(IOException e) {
			}
			@Override
			public HttpServerHandler create() {
				FileHttpServerHandler h = new FileHttpServerHandler(root).setIndex(index);
				for (Map.Entry<String, String> e : mime.entrySet()) {
					h.setContentType(e.getKey(), e.getValue());
				}
				return h;
			}
			@Override
			public void closed() {
			}
		});
	}
}
