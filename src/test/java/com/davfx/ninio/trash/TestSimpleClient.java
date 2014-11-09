package com.davfx.ninio.trash;

import com.davfx.ninio.http.util.InMemoryPost;
import com.davfx.ninio.http.util.Parameters;
import com.davfx.ninio.http.util.SimpleHttpClient;
import com.davfx.ninio.http.util.SimpleHttpClientHandler;

public class TestSimpleClient {
	public static void main(String[] args) throws Exception {
		new SimpleHttpClient().withHost("localhost").withPort(8080).on("/path?foo=bar").send(new SimpleHttpClientHandler() {
			@Override
			public void handle(int status, String reason, Parameters parameters, InMemoryPost body) {
				System.out.println("[" + status + "] " + reason + " / " + body);
			}
		});
/*
		final Queue queue = new Queue();
		final SimpleHttpClient client = new SimpleHttpClient(queue, new Trust(new File("testkeys"), "passphrase"));

		HttpQueryBuilder query = new HttpQueryBuilder("/");
		query.add("a", "aa");
		String httpPath = query.toString();
		
		client.get(new Address("localhost", 8080), true, httpPath, new SimpleHttpClientHandler() {
			@Override
			public void handle(int status, String reason, InMemoryPost body) {
				if (body == null) {
					System.out.println("ERRRORRRR");
				} else {
					System.out.println("[" + status + "] " + reason + " / " + body.toString());
				}
			}
		});
		Thread.sleep(10000);
		client.get(new Address("localhost", 8080), true, httpPath, new SimpleHttpClientHandler() {
			@Override
			public void handle(int status, String reason, InMemoryPost body) {
				if (body == null) {
					System.out.println("2 ERRRORRRR");
				} else {
					System.out.println("2 [" + status + "] " + reason + " / " + body.toString());
				}
				queue.close();
				client.close();
			}
		});*/
	}
}
