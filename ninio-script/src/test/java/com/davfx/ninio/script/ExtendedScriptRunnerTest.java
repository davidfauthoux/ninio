package com.davfx.ninio.script;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpServer;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;
import com.davfx.script.ScriptRunner;
import com.davfx.script.SyncScriptFunction;
import com.davfx.util.Lock;
import com.google.common.base.Charsets;
import com.google.gson.JsonElement;

public class ExtendedScriptRunnerTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedScriptRunnerTest.class);
	
	@Test
	public void testHttp() throws Exception {
		try (Queue queue = new Queue(); HttpServer server = new HttpServer(queue, null, new Address(Address.ANY, 8080), new HttpServerHandlerFactory() {
			@Override
			public void failed(IOException e) {
				LOGGER.warn("Failed", e);
			}
			@Override
			public void closed() {
			}
			
			@Override
			public HttpServerHandler create() {
				return new HttpServerHandler() {
					private HttpRequest request;
					
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Failed", e);
					}
					@Override
					public void close() {
						LOGGER.debug("Closed");
					}
					
					@Override
					public void handle(HttpRequest request) {
						LOGGER.debug("Request received: {}", request);
						this.request = request;
					}

					@Override
					public void handle(Address address, ByteBuffer buffer) {
						LOGGER.debug("Post received: {}", new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8));
					}
					
					@Override
					public void ready(Write write) {
						LOGGER.debug("Ready to write");
						write.write(new HttpResponse());
						write.handle(null, ByteBuffer.wrap(("hello:" + request.path).getBytes(Charsets.UTF_8)));
						write.close();
					}
					
				};
			}
			
		})) {
			Thread.sleep(100);
			try (ExtendedScriptRunner runner = new ExtendedScriptRunner()) {
				final Lock<JsonElement, Exception> lock = new Lock<>();
				ScriptRunner.Engine engine = runner.runner.engine();
				engine.register("out", new SyncScriptFunction() {
					@Override
					public JsonElement call(JsonElement request) {
						lock.set(request);
						return null;
					}
				});
				engine.eval("http({url:'http://127.0.0.1:8080/helloworld'}, function(r) { console.debug(r); out(r); });", null);
				Assertions.assertThat(lock.waitFor().getAsString()).isEqualTo("hello:/helloworld");
			}
		}
	}

}
