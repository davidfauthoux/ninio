package com.davfx.ninio.http.util;

import java.io.IOException;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.http.HttpClientConfigurator;
import com.davfx.ninio.http.HttpServer;
import com.davfx.ninio.http.HttpServerConfigurator;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class ServiceHttpServer {
	private static final Config CONFIG = ConfigFactory.load();

	public static void main(String[] args) throws Exception {
		Queue queue = new Queue();
		final OnTheFlyImageToJpegConverter converter = new OnTheFlyImageToJpegConverter(new HttpClientConfigurator(queue));
		new HttpServer(new HttpServerConfigurator(queue).withAddress(new Address(CONFIG.getString("http.service.bind.host"), CONFIG.getInt("http.service.bind.port"))), new HttpServerHandlerFactory() {
			@Override
			public void failed(IOException e) {
			}
			@Override
			public HttpServerHandler create() {
				return new PathDispatchHttpServerHandler().add("/image.convert", converter.create());
			}
			
			@Override
			public void closed() {
			}
		});
	}
}
