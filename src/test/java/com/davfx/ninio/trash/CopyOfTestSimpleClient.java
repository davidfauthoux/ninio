package com.davfx.ninio.trash;

import com.davfx.ninio.http.util.HttpQueryBuilder;
import com.davfx.ninio.http.util.InMemoryPost;
import com.davfx.ninio.http.util.SimpleHttpClient;
import com.davfx.ninio.http.util.SimpleHttpClientHandler;

public class CopyOfTestSimpleClient {
	public static void main(String[] args) throws Exception {
		new SimpleHttpClient().withPort(8080).on(new HttpQueryBuilder("/toto").add("echo", "titi").toString()).send(new SimpleHttpClientHandler() {
			@Override
			public void handle(int status, String reason, InMemoryPost body) {
				if (body == null) {
					System.out.println("ERROR");
				} else {
					System.out.println("[" + status + "] " + reason + " / " + body.toString());
				}
			}
		});
	}
}
