package com.davfx.ninio.trash;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.ByteBufferHandler;
import com.davfx.ninio.http.Http;
import com.davfx.ninio.http.HttpClient;
import com.davfx.ninio.http.HttpClientConfigurator;
import com.davfx.ninio.http.HttpClientHandler;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.util.DefaultSimpleHttpServerHandler;
import com.davfx.ninio.http.util.InMemoryPost;
import com.davfx.ninio.http.util.Parameters;
import com.davfx.ninio.http.util.SimpleHttpServer;
import com.davfx.ninio.proxy.Forward;
import com.davfx.ninio.proxy.ProxyClient;
import com.davfx.ninio.proxy.ProxyServer;
import com.davfx.ninio.proxy.ProxyUtils;
import com.davfx.ninio.proxy.Reproxy;

public class TestClient {
	public static void main(String[] args) throws Exception {
		System.out.println("LAUNCHING HTTP SERVER");
		SimpleHttpServer.bind(8080).start(new DefaultSimpleHttpServerHandler() {
			@Override
			public String get(String path, Parameters parameters) {
				return "ECHO GET " + path + " " + parameters;
			}
			@Override
			public String post(String path, Parameters parameters, InMemoryPost post) {
				return "ECHO POST " + path + " " + parameters + " " + post.toString();
			}
		});

		Thread.sleep(1000);
		System.out.println("LAUNCHING PROXY 6666");
		new ProxyServer(6666, 10).start();
		Thread.sleep(1000);
		System.out.println("LAUNCHING PROXY 7777");
		new ProxyServer(7777, 10).override(ProxyUtils.SOCKET_TYPE, Forward.forward(new Address("localhost", 6666))).start();
		Thread.sleep(1000);
		System.out.println("LAUNCHING PROXY 9999");
		new ProxyServer(9999, 10).override(Reproxy.DEFAULT_TYPE, Reproxy.server()).start();
		
		Thread.sleep(1000);
		System.out.println("LAUNCHING HTTP CLIENT");
		HttpClient client = new HttpClient(new HttpClientConfigurator()
			//client.override(new ProxyClient(new Address("localhost", 7777)).socket());
			.override(new ProxyClient(new Address("localhost", 9999)).override(Reproxy.DEFAULT_TYPE, Reproxy.client(new Address("localhost", 7777), ProxyUtils.SOCKET_TYPE)).of(Reproxy.DEFAULT_TYPE))
			);
			HttpRequest r = new HttpRequest(new Address("localhost", 8080), false, HttpRequest.Method.GET, "/");
			r.getHeaders().put(Http.ACCEPT_ENCODING, Http.GZIP);
			client.send(r, new HttpClientHandler() {
				@Override
				public void ready(ByteBufferHandler write) {
				}
				@Override
				public void handle(Address address, ByteBuffer buffer) {
					byte[] b = new byte[buffer.remaining()];
					buffer.get(b);
					System.out.println("RECEIVED:" + new String(b, Charset.forName("UTF-8")) + "/");
				}
				
				@Override
				public void received(HttpResponse response) {
					System.out.println("RESPONSE: " + response.getStatus() + " " + response.getReason() + " / " + response.getHeaders());
				}
				
				@Override
				public void close() {
					System.out.println("FINISHED");
				}
				
				@Override
				public void failed(IOException e) {
					System.out.println("FAILED " + e.getMessage());
				}
			});
			Thread.sleep(1000);
			client.send(r, new HttpClientHandler() {
				@Override
				public void ready(ByteBufferHandler write) {
				}
				@Override
				public void handle(Address address, ByteBuffer buffer) {
					byte[] b = new byte[buffer.remaining()];
					buffer.get(b);
					System.out.println("RECEIVED:" + new String(b, Charset.forName("UTF-8")) + "/");
				}
				
				@Override
				public void received(HttpResponse response) {
					System.out.println("RESPONSE: " + response.getStatus() + " " + response.getReason() + " / " + response.getHeaders());
				}
				
				@Override
				public void close() {
					System.out.println("FINISHED");
				}
				
				@Override
				public void failed(IOException e) {
					System.out.println("FAILED " + e.getMessage());
				}
			});
			Thread.sleep(1000);
	}
}
