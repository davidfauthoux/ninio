package com.davfx.ninio.trash;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.http.Http;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpServer;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;

public class TestServer {
	public static void main(String[] args) throws Exception {
		Queue queue = new Queue();
		new HttpServer(queue, new Address(8080), new HttpServerHandlerFactory() {
			@Override
			public void failed(IOException e) {
				System.out.println("FAILED");
			}
			
			@Override
			public HttpServerHandler create() {
				return new HttpServerHandler() {
					
					@Override
					public void ready(Write write) {
						write.write(new HttpResponse(Http.Status.OK, Http.Message.OK));
						write.handle(null, ByteBuffer.wrap("HELLO".getBytes(Http.UTF8_CHARSET)));
						write.close();
					}
					
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						// POST here
					}
					
					@Override
					public void handle(HttpRequest request) {
						System.out.println("REQUEST: " + request.getPath() + " / " + request.getHeaders());
					}
					
					@Override
					public void failed(IOException e) {
						System.out.println("INNER FAILED");
					}
					
					@Override
					public void close() {
						System.out.println("INNER CLOSED");
					}
				};
			}
			
			@Override
			public void closed() {
				System.out.println("CLOSED");
			}
		});
	}
}
