package com.davfx.ninio.http;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connected;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Listener;
import com.davfx.ninio.core.Listening;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.Nop;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.core.Timeout;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class WebsocketTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketTest.class);
	
	private static Disconnectable server(Ninio ninio, int port) throws IOException {
		final byte[] indexHtml= Files.toByteArray(new File("src/test/resources/files/ws.html"));
		
		Listener tcp = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)));
		tcp.listen(ninio.create(HttpListening.builder().with(new HttpListeningHandler() {
			@Override
			public HttpContentReceiver handle(HttpRequest request, HttpResponseSender responseHandler) {
				LOGGER.debug("----> {}", request);
				if (request.path.equals("/ws")) {
					return new WebsocketHttpContentReceiver(request, responseHandler, false, new Listening() {
						@Override
						public void failed(IOException e) {
							LOGGER.warn("Socket failed <--", e);
						}
						@Override
						public void connected(Address address) {
							LOGGER.debug("Socket connected <--");
						}
						@Override
						public void closed() {
							LOGGER.debug("Socket closed <--");
						}
						@Override
						public Connection connecting(final Connected connecting) {
							return new Connection() {
								@Override
								public void connected(Address address) {
								}
								@Override
								public void received(Address address, ByteBuffer buffer) {
									String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
									LOGGER.debug("Received {} <--: {}", address, s);
									connecting.send(null, ByteBuffer.wrap(("ECHO " + s).getBytes(Charsets.UTF_8)), new Nop());
								}
								@Override
								public void closed() {
								}
								@Override
								public void failed(IOException e) {
								}
							};
						}
					});
				} else if (request.path.equals("/")) {
					responseHandler.send(HttpResponse.ok()).send(ByteBuffer.wrap(indexHtml), new Nop()).finish();
					//responseHandler.send(new HttpResponse(HttpStatus.OK, HttpMessage.OK, ImmutableMultimap.of(HttpHeaderKey.CONTENT_LENGTH, String.valueOf(indexHtml.length)))).send(ByteBuffer.wrap(indexHtml)).finish();;
					return null;
				} else {
					responseHandler.send(HttpResponse.notFound()).finish();
					return null;
				}
			}
			@Override
			public void closed() {
			}
			@Override
			public void connected(Address address) {
			}
			@Override
			public void failed(IOException ioe) {
			}
		})));
		return tcp;
	}
	
	public static void main(String[] args) throws Exception {
		int port = 8080;
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			try (Disconnectable tcp = server(ninio, port)) {
				System.out.println("http://" + new Address(Address.LOCALHOST, port) + "/");
				Thread.sleep(60000);
			}
		}
	}
}
