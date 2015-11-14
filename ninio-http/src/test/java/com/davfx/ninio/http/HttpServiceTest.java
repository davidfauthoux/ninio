package com.davfx.ninio.http;

import java.io.IOException;
import java.io.InputStream;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.http.util.HttpService;
import com.davfx.ninio.http.util.HttpServiceHandler;
import com.davfx.ninio.http.util.HttpServiceResult;
import com.davfx.util.Wait;
import com.google.common.base.Charsets;

public final class HttpServiceTest {
	
	public static void main(String[] args) {
		Wait wait = new Wait();
		int port = 8080;
		try (HttpService server = new HttpService()) {
			server
			.register("/echo", new HttpServiceHandler() {
				@Override
				public void handle(HttpRequest request, InputStream post, HttpServiceResult result) {
					/*
					for (int i = 0; i < 5; i++) {
						System.out.println("#" + i);
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
						}
					}
					*/
					result.success(HttpContentType.PLAIN_TEXT, "echo " + request.path.parameters.get("message").iterator().next());
				}
			})
			.register("/echostream", new HttpServiceHandler() {
				@Override
				public void handle(HttpRequest request, InputStream post, HttpServiceResult result) throws IOException {
					/*
					for (int i = 0; i < 5; i++) {
						System.out.println("#" + i);
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
						}
					}
					*/
					result.success(HttpContentType.PLAIN_TEXT).write(("echo " + request.path.parameters.get("message").iterator().next()).getBytes(Charsets.UTF_8));
				}
			})
			.start(port);

			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/echo?message=helloworld");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/echostream?message=helloworld");
			wait.waitFor();
		}
	}
	
	@SuppressWarnings("resource")
	public static void main2(String[] args) {
		new HttpService()
		.register("/echo", new HttpServiceHandler() {
			@Override
			public void handle(HttpRequest request, InputStream post, HttpServiceResult result) {
				String echo = "echo " + request.path.parameters.get("message").iterator().next();
				result.success(HttpContentType.PLAIN_TEXT, echo);
			}
		})
		.register("/echostream", new HttpServiceHandler() {
			@Override
			public void handle(HttpRequest request, InputStream post, HttpServiceResult result) throws IOException {
				String echo = "echo " + request.path.parameters.get("message").iterator().next();
				result.success(HttpContentType.PLAIN_TEXT).write(echo.getBytes(Charsets.UTF_8));
			}
		})
		.start(8080);
	}
	
}
