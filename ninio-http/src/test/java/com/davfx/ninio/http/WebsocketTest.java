package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.ninio.http.websocket.WebsocketHttpServerHandler;
import com.davfx.ninio.util.GlobalQueue;
import com.davfx.util.Wait;
import com.google.common.base.Charsets;

public final class WebsocketTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketTest.class);
	
	public static void main(String[] args) {
		Wait wait = new Wait();
		try (HttpServer server = new HttpServer(GlobalQueue.get(), null, new Address(Address.ANY, 8080), new HttpServerHandlerFactory() {
			@Override
			public void failed(IOException e) {
			}
			@Override
			public void closed() {
			}
			
			@Override
			public HttpServerHandler create() {
				return new WebsocketHttpServerHandler(true, new ReadyConnection() {
					private CloseableByteBufferHandler write;
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
						LOGGER.debug("Received {} <--: {}", address, s);
						write.handle(null, ByteBuffer.wrap(("echo " + s).getBytes(Charsets.UTF_8)));
					}
					
					@Override
					public void connected(FailableCloseableByteBufferHandler write) {
						LOGGER.debug("Connected <--");
						this.write = write;
					}
					
					@Override
					public void close() {
						LOGGER.debug("Closed <--");
					}
					
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Failed <--", e);
					}
				});
			}
			
		})) {
			wait.waitFor();
		}
	}
}
