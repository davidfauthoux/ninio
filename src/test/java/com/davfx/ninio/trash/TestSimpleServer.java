package com.davfx.ninio.trash;

import com.davfx.ninio.http.util.DefaultSimpleHttpServerHandler;
import com.davfx.ninio.http.util.InMemoryPost;
import com.davfx.ninio.http.util.Parameters;
import com.davfx.ninio.http.util.SimpleHttpServer;

public class TestSimpleServer {
	public static void main(String[] args) throws Exception {
		new SimpleHttpServer().withPort(8080).start(new DefaultSimpleHttpServerHandler() {
			@Override
			public String get(String path, Parameters parameters) {
				return "ECHO GET " + path + " " + parameters;
			}
			@Override
			public String post(String path, Parameters parameters, InMemoryPost post) {
				return "ECHO POST " + path + " " + parameters + " " + post.toString();
			}
		});
		/*
		Queue queue = new Queue();
		new SimpleHttpServer(queue, new Address(8080), new DirectorySimpleHttpServerHandler(new File(".")));
		*/
		/*
		new SimpleHttpServer(queue, new Trust(new File("testkeys"), "passphrase"), new Address(8080), new SimpleHttpServer.Handler() {
			@Override
			public String get(String path, Parameters parameters) {
				System.out.println("GET " + path);
				return "ECHO " + path;
			}
			@Override
			public String post(String path, Parameters parameters, InMemoryPost post) {
				return "ECHO " + post.toString();
			}
			@Override
			public String head(String path, Parameters parameters) {
				return null;
			}
			@Override
			public String delete(String path, Parameters parameters) {
				return null;
			}
			@Override
			public String put(String path, Parameters parameters) {
				return null;
			}
		});*/
	}
}
