package com.davfx.ninio.http.v3;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;

public class Test {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Test.class);
	
	public static void main(String[] args) throws Exception {
		try (HttpClient client = new HttpClient()) {
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
					LOGGER.debug("Received {}", new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining()));
				}
				@Override
				public void ended() {
					LOGGER.debug("ENDED");
				}
			});
			r.create(HttpRequest.of("http://david.fauthoux.free.fr")).finish();
			Thread.sleep(10000);
		}
	}
}
