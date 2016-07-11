package com.davfx.ninio.http;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.After;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Buffering;
import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Connector;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.Listening;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.Receiver;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.core.Timeout;
import com.davfx.ninio.util.SerialExecutor;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class WebsocketTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketTest.class);
	
	@After
	public void waitALittleBit() throws Exception {
		Thread.sleep(100);
	}
	
	private static Disconnectable server(Ninio ninio, int port) throws IOException {
		final byte[] indexHtml= Files.toByteArray(new File("src/test/resources/files/ws.html"));
		
		Disconnectable tcp = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)).listening(HttpListening.builder().with(new SerialExecutor(WebsocketTest.class)).with(new HttpListeningHandler() {
			@Override
			public ConnectionHandler create() {
				return new ConnectionHandler() {
					@Override
					public HttpContentReceiver handle(HttpRequest request, ResponseHandler responseHandler) {
						LOGGER.debug("----> {}", request);
						if (request.path.equals("/ws")) {
							return new WebsocketHttpContentReceiver(request, responseHandler, false, new Listening() {
								@Override
								public Connection connecting(Address from, Connector connector) {
									return new Connection() {
										public Failing failing() {
											return new Failing() {
												@Override
												public void failed(IOException e) {
													LOGGER.warn("Socket failed <--", e);
												}
											};
										}
										public Connecting connecting() {
											return new Connecting() {
												@Override
												public void connected(Connector connector, Address address) {
													LOGGER.debug("Socket connected <--");
												}
											};
										}
										public Closing closing() {
											return new Closing() {
												@Override
												public void closed() {
													LOGGER.debug("Socket closed <--");
												}
											};
										}
										public Receiver receiver() {
											return new Receiver() {
												@Override
												public void received(Connector connector, Address address, ByteBuffer buffer) {
													String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
													LOGGER.debug("Received {} <--: {}", address, s);
													connector.send(null, ByteBuffer.wrap(("ECHO " + s).getBytes(Charsets.UTF_8)));
												}
											};
										}
										@Override
										public Buffering buffering() {
											return null;
										}
									};
								}
							});
						} else if (request.path.equals("/")) {
							responseHandler.send(HttpResponse.ok()).send(ByteBuffer.wrap(indexHtml)).finish();;
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
					public void buffering(long size) {
					}
				};
			}
		}).build()));
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
