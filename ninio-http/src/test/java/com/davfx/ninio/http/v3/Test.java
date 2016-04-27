package com.davfx.ninio.http.v3;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.Ninio;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;

//TODO Location:
public class Test {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
	
	public static void main(String[] args) throws Exception {
		try (Ninio ninio = Ninio.create()) {
		/*
		try (Http http = new Http()) {
			http.send(new HttpRequest(new Address("david.fauthoux.free.fr", 80), false, HttpMethod.GET, HttpPath.of("/pizzapassion")), null, new Http.InMemoryHandler() {
				
				@Override
				public void failed(IOException e) {
					LOGGER.error("Failed", e);
				}
				
				@Override
				public void handle(HttpResponse response, InMemoryBuffers content) {
					String s = content.toString();
					LOGGER.debug("Received: {}", s);
				}
			});
			
			Thread.sleep(100000);
		}
		*/

		String url = "http://david.fauthoux.free.fr/pizzapassion/index.css";
		url = "http://www.this-page-intentionally-left-blank.org/index.html";
		url = "http://google.com";
		
		try (HttpClient client = ninio.create(HttpClient.builder())) {
			{
				client.request()
				.failing(new Failing() {
					@Override
					public void failed(IOException e) {
						LOGGER.error("Failed", e);
					}
				})
				.receiving(new HttpReceiver() {
					@Override
					public void received(HttpClient client, HttpResponse response) {
						LOGGER.debug("RESPONSE {}", response);
					}
					
					@Override
					public void received(HttpClient client, ByteBuffer buffer) {
						LOGGER.debug("Received {}", new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining()));
					}
					@Override
					public void ended(HttpClient client) {
						LOGGER.debug("ENDED");
					}
				})
				.build()
				.create(HttpRequest.of(url)).finish();
				Thread.sleep(5000);
			}
			{
				client.request()
				.failing(new Failing() {
					@Override
					public void failed(IOException e) {
						LOGGER.error("Failed", e);
					}
				})
				.receiving(new HttpReceiver() {
					@Override
					public void received(HttpClient client, HttpResponse response) {
						LOGGER.debug("RESPONSE {}", response);
					}
					
					@Override
					public void received(HttpClient client, ByteBuffer buffer) {
						LOGGER.debug("Received {}", new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining()));
					}
					@Override
					public void ended(HttpClient client) {
						LOGGER.debug("ENDED");
					}
				})
				.build()
				.create(HttpRequest.of(url)).finish();
				Thread.sleep(10000000);
			}
		}
		}
	}
}
