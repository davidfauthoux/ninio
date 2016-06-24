package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferUtils;
import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Connector;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.EchoReceiver;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.Listening;
import com.davfx.ninio.core.LockClosing;
import com.davfx.ninio.core.LockFailing;
import com.davfx.ninio.core.LockReceiver;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.Receiver;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.core.Timeout;
import com.davfx.ninio.core.WaitClosing;
import com.davfx.ninio.core.WaitConnecting;
import com.davfx.ninio.core.WaitListenConnecting;
import com.davfx.ninio.http.HttpClient;
import com.davfx.ninio.http.HttpContentReceiver;
import com.davfx.ninio.http.HttpListening;
import com.davfx.ninio.http.HttpListeningHandler;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.WebsocketHttpContentReceiver;
import com.davfx.ninio.http.WebsocketSocket;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.SerialExecutor;
import com.davfx.ninio.util.Wait;

public class WebsocketClientServerTest {
	
	@Test
	public void test() throws Exception {
		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		final Wait waitServerConnecting = new Wait();
		final Wait waitServerClosing = new Wait();
		final Wait waitClientConnecting = new Wait();
		Wait wait = new Wait();

		int port = 8080;
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			try (Disconnectable tcp = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port))
					.failing(new LockFailing(lock))
					.connecting(new WaitListenConnecting(wait))
					.listening(HttpListening.builder().with(new SerialExecutor(WebsocketClientServerTest.class)).with(new HttpListeningHandler() {
				@Override
				public ConnectionHandler create() {
					return new ConnectionHandler() {
						@Override
						public HttpContentReceiver handle(HttpRequest request, final ResponseHandler responseHandler) {
							return new WebsocketHttpContentReceiver(request, responseHandler, true, new Listening() {
								@Override
								public Connection connecting(Address from, Connector connector) {
									return new Connection() {
										public Failing failing() {
											return new LockFailing(lock);
										}
										public Connecting connecting() {
											return new WaitConnecting(waitServerConnecting);
										}
										public Closing closing() {
											return new WaitClosing(waitServerClosing);
										}
										public Receiver receiver() {
											return new EchoReceiver();
										}
									};
								}
							});
						}
						@Override
						public void closed() {
							waitServerClosing.run();
						}
					};
				}
			}).build()))) {
				
				wait.waitFor();

				try (HttpClient httpClient = ninio.create(HttpClient.builder().with(new SerialExecutor(HttpGetTest.class)))) {
					
					try (Connector client = ninio.create(WebsocketSocket.builder().with(httpClient).to(new Address(Address.LOCALHOST, port))
						.failing(new LockFailing(lock))
						.closing(new LockClosing(lock))
						.receiving(new LockReceiver(lock))
						.connecting(new WaitConnecting(waitClientConnecting)))) {

						client.send(null, ByteBufferUtils.toByteBuffer("test"));

						waitClientConnecting.waitFor();
						waitServerConnecting.waitFor();
						Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("test");
					}

					waitServerClosing.waitFor();
				}
			}
		}
	}

}
