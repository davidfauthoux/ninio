package com.davfx.ninio.http;

import java.io.OutputStream;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.http.util.HttpController;
import com.davfx.ninio.http.util.HttpPost;
import com.davfx.ninio.http.util.HttpService;
import com.davfx.ninio.http.util.HttpServiceHandler;
import com.davfx.util.Wait;
import com.google.common.base.Charsets;

public final class ReadmeWithHttpService {
	
	
	private static void sleep() {
		for (int i = 0; i < 5; i++) {
			System.out.println("#" + i);
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
		}
	}
	
	public static void main(String[] args) {
		Wait wait = new Wait();
		int port = 8080;
		try (HttpService server = new HttpService()) {
			server
			.register(new SubPathHttpRequestFilter(HttpQueryPath.of("/echo/string")), new HttpServiceHandler() {
				@Override
				public HttpController.Http handle(HttpRequest request, HttpPost post) throws Exception {
					sleep();
					return HttpController.Http.ok().content("echo/string " + request.path.parameters.get("message").iterator().next());
				}
			})
			.register(new SubPathHttpRequestFilter(HttpQueryPath.of("/echo/stream")), new HttpServiceHandler() {
				@Override
				public HttpController.Http handle(final HttpRequest request, HttpPost post) throws Exception {
					sleep();
					return HttpController.Http.ok().stream(new HttpController.HttpStream() {
						@Override
						public void produce(OutputStreamFactory output) throws Exception {
							try (OutputStream out = output.open()) {
								out.write(("echo/stream " + request.path.parameters.get("message").iterator().next()).getBytes(Charsets.UTF_8));
							}
						}
					});
				}
			})
			.start(port);

			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/echo/string?message=helloworld");
			System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/echo/stream?message=helloworld");
			wait.waitFor();
		}
	}
	
}
