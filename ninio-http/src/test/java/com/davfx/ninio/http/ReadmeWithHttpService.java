package com.davfx.ninio.http;

import java.io.IOException;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.http.util.HttpPost;
import com.davfx.ninio.http.util.HttpService;
import com.davfx.ninio.http.util.HttpServiceHandler;
import com.davfx.ninio.http.util.HttpServiceResult;
import com.davfx.util.Wait;
import com.google.common.base.Charsets;

public final class ReadmeWithHttpService {
	
	public static void main(String[] args) {
		Wait wait = new Wait();
		int port = 8080;
		try (HttpService server = new HttpService()) {
			server
			.register(new SubPathHttpRequestFilter(HttpQueryPath.of("/echo/string")), new HttpServiceHandler() {
				@Override
				public void handle(HttpRequest request, HttpPost post, HttpServiceResult result) {
					/*
					for (int i = 0; i < 5; i++) {
						System.out.println("#" + i);
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
						}
					}
					*/
					result.out("echo/string " + request.path.parameters.get("message").iterator().next());
				}
			})
			.register(new SubPathHttpRequestFilter(HttpQueryPath.of("/echo/stream")), new HttpServiceHandler() {
				@Override
				public void handle(HttpRequest request, HttpPost post, HttpServiceResult result) throws IOException {
					/*
					for (int i = 0; i < 5; i++) {
						System.out.println("#" + i);
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
						}
					}
					*/
					result.out().write(("echo/stream " + request.path.parameters.get("message").iterator().next()).getBytes(Charsets.UTF_8));
				}
			})
			.start(port);

			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/echo/string?message=helloworld");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/echo/stream?message=helloworld");
			wait.waitFor();
		}
	}
	
}
