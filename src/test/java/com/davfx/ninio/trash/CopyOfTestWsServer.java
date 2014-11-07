package com.davfx.ninio.trash;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Pattern;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.http.HttpClient;
import com.davfx.ninio.http.HttpClientConfigurator;
import com.davfx.ninio.http.HttpServer;
import com.davfx.ninio.http.HttpServerConfigurator;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;
import com.davfx.ninio.http.util.PatternDispatchHttpServerHandler;
import com.davfx.ninio.http.websocket.WebsocketHttpServerHandler;
import com.davfx.ninio.http.websocket.WebsocketReady;
import com.davfx.ninio.telnet.TelnetClient;
import com.davfx.ninio.telnet.TelnetClientConfigurator;
import com.davfx.ninio.telnet.TelnetClientHandler;
import com.google.common.base.Charsets;

//Sec-WebSocket-Key=Jc0pxsIaE4PCWb0zhx4Hhw==
//Cache-Control=no-cache
//Connection=Upgrade
//Sec-WebSocket-Version=13
//Upgrade=websocket
//Sec-WebSocket-Extensions=permessage-deflate; client_max_window_bits, x-webkit-deflate-frame

/*
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: HSmrc0sMlYUkAGmm5OPpG2HaGWk=
Sec-WebSocket-Protocol: chat
 */
public class CopyOfTestWsServer {
	public static void main(String[] args) throws Exception {
		final ScheduledExecutorService eee = Executors.newSingleThreadScheduledExecutor(); 
		try (Queue queue = new Queue()) {
			final HttpClient ccc = new HttpClient(new HttpClientConfigurator(queue));
			new HttpServer(new HttpServerConfigurator(queue).withPort(8080), new HttpServerHandlerFactory() {
				@Override
				public void failed(IOException e) {
				}
				@Override
				public void closed() {
				}
				
				@Override
				public HttpServerHandler create() {
					return new PatternDispatchHttpServerHandler()
					// .add(Pattern.compile(".*\\.html"), new JsonDirectoryHttpServerHandler(new File(".")))
					.add(Pattern.compile(".*"), new WebsocketHttpServerHandler(new ReadyConnection() {
						@Override
						public void failed(IOException e) {
							System.out.println("### FAILED");
						}
						@Override
						public void close() {
							System.out.println("### CLOSE");
						}
						
						CloseableByteBufferHandler write;
						@Override
						public void handle(Address address, ByteBuffer buffer) {
							byte[] b = new byte[buffer.remaining()];
							buffer.get(b);
							System.out.println("### HANDLE " + new String(b, Charsets.UTF_8) + "/");
							write.handle(null, ByteBuffer.wrap(b));
						}
						
						@Override
						public void connected(FailableCloseableByteBufferHandler write) {
							System.out.println("### CONNECTED");
							this.write = write;
							
						}
					}));
				}
			});

			new TelnetClient(new TelnetClientConfigurator(queue).withAddress(new Address("localhost", 8080)).override(new ReadyFactory() {
				@Override
				public Ready create(Queue queue) {
					return new WebsocketReady(ccc);
				}
			})).connect(new TelnetClientHandler() {
				@Override
				public void failed(IOException e) {
					System.out.println("@ FAILED");
				}
				@Override
				public void close() {
					System.out.println("@ CLOSE");
				}
				@Override
				public void received(String text) {
					if (text.isEmpty()) {
						return;
					}
					System.out.println("@ RECEIVED " + text);
					queue.close();
					ccc.close();
					eee.shutdown();
				}
				@Override
				public void launched(Callback callback) {
					System.out.println("@ LAUNCHED");
					callback.send("TOTO");
				}
			});
			
			Thread.sleep(1000);
		}			
	}
}
