package com.davfx.ninio.trash;

import com.davfx.ninio.http.util.DefaultSimpleHttpServerHandler;
import com.davfx.ninio.http.util.Parameters;
import com.davfx.ninio.http.util.PathDispatchSimpleHttpServerHandler;
import com.davfx.ninio.http.util.SimpleHttpServer;

public class Copy_2_of_TestSimpleServer {
	public static void main(String[] args) throws Exception {
		new SimpleHttpServer(8080).start(new PathDispatchSimpleHttpServerHandler().add("/toto", new DefaultSimpleHttpServerHandler() {
			@Override
			public String get(String path, Parameters parameters) {
				System.out.println("GET " + path);
				return "ECHO " + parameters.getValue("echo");
			}
		}));
	}
}
