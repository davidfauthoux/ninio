package com.davfx.ninio.trash;

import java.io.File;
import java.io.IOException;

import com.davfx.ninio.http.HttpServer;
import com.davfx.ninio.http.HttpServerConfigurator;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;
import com.davfx.ninio.http.util.HtmlDirectoryHttpServerHandler;
import com.davfx.ninio.http.util.PathDispatchHttpServerHandler;

public class ImageServer {
	public static void main(String[] args) throws Exception {
		new HttpServer(new HttpServerConfigurator().withPort(8080), new HttpServerHandlerFactory() {
			@Override
			public void failed(IOException e) {
			}
			
			@Override
			public HttpServerHandler create() {
				return new PathDispatchHttpServerHandler().add("/", new HtmlDirectoryHttpServerHandler(new File(".")));
			}
			
			@Override
			public void closed() {
			}
		});
	}
}
