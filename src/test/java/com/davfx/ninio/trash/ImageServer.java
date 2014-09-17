package com.davfx.ninio.trash;

import java.io.File;
import java.io.IOException;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.http.HttpServer;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;
import com.davfx.ninio.http.util.HtmlDirectoryHttpServerHandler;
import com.davfx.ninio.http.util.PathDispatchHttpServerHandler;

public class ImageServer {
	public static void main(String[] args) throws Exception {
		Queue queue = new Queue();
		new HttpServer(queue, new Address(8080), new HttpServerHandlerFactory() {
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
