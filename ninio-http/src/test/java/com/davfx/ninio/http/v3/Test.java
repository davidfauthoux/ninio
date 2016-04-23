package com.davfx.ninio.http.v3;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;

//TODO Location:
public class Test {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
	
	public static void main(String[] args) throws Exception {
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

		try (HttpClient client = new HttpClient()) {
			SnmpRequest r = client.request();

			r.failing(new Failing() {
				@Override
				public void failed(IOException e) {
					LOGGER.error("FAILED", e);
				}
			});
			
			r.receiving(new SnmpReceiver() {
				@Override
				public void received(HttpResponse response) {
					LOGGER.debug("RESPONSE {}", response);
				}
				
				@Override
				public void received(ByteBuffer buffer) {
					LOGGER.debug("RECEIVED {}", new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining()));
				}
				@Override
				public void ended() {
					LOGGER.debug("ENDED");
				}
			});
			
			r.create(HttpRequest.of("http://www.this-page-intentionally-left-blank.org/index.html")).finish();
			Thread.sleep(5000);
		}
		
		try (HttpClient client = new HttpClient()) {
			{
				SnmpRequest r = client.request();
				r.failing(new Failing() {
					@Override
					public void failed(IOException e) {
						LOGGER.error("Failed", e);
					}
				});
				r.receiving(new SnmpReceiver() {
					@Override
					public void received(HttpResponse response) {
						LOGGER.debug("RESPONSE {}", response);
					}
					
					@Override
					public void received(ByteBuffer buffer) {
						LOGGER.debug("Received {}", buffer.remaining());// new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining()));
					}
					@Override
					public void ended() {
						LOGGER.debug("ENDED");
					}
				});
				r.create(HttpRequest.of(url)).finish();
				Thread.sleep(5000);
			}
			{
				SnmpRequest r = client.request();
				r.failing(new Failing() {
					@Override
					public void failed(IOException e) {
						LOGGER.error("Failed", e);
					}
				});
				r.receiving(new SnmpReceiver() {
					@Override
					public void received(HttpResponse response) {
						LOGGER.debug("RESPONSE {}", response);
					}
					
					@Override
					public void received(ByteBuffer buffer) {
						LOGGER.debug("Received {}", buffer.remaining());// new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining()));
					}
					@Override
					public void ended() {
						LOGGER.debug("ENDED");
					}
				});
				r.create(HttpRequest.of(url)).finish();
				Thread.sleep(10000000);
			}
		}
	}
}
