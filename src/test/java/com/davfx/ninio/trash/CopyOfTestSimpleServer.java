package com.davfx.ninio.trash;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import com.davfx.ninio.http.HttpServer;
import com.davfx.ninio.http.HttpServerConfigurator;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;
import com.davfx.ninio.http.util.JsonDirectoryHttpServerHandler;
import com.davfx.ninio.http.util.PatternDispatchHttpServerHandler;

public class CopyOfTestSimpleServer {
	public static void main(String[] args) throws Exception {
		new HttpServer(new HttpServerConfigurator().withPort(8080), new HttpServerHandlerFactory() {
			@Override
			public void failed(IOException e) {
			}
			
			@Override
			public HttpServerHandler create() {
				return new PatternDispatchHttpServerHandler().add(Pattern.compile(".*"), new JsonDirectoryHttpServerHandler(new File(".")));
			}
			
			@Override
			public void closed() {
			}
		});
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
