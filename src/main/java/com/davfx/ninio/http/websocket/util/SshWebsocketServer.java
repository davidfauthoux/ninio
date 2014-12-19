package com.davfx.ninio.http.websocket.util;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.common.SocketReadyFactory;
import com.davfx.ninio.http.HttpServer;
import com.davfx.ninio.http.HttpServerConfigurator;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;
import com.davfx.ninio.http.util.JsonDirectoryHttpServerHandler;
import com.davfx.ninio.http.util.PatternDispatchHttpServerHandler;
import com.davfx.ninio.http.websocket.WebsocketHttpServerHandler;
import com.davfx.ninio.ssh.SshClient;
import com.davfx.ninio.ssh.SshClientConfigurator;
import com.davfx.ninio.telnet.TelnetClientConfigurator;
import com.google.common.base.Charsets;

public final class SshWebsocketServer {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SshWebsocketServer.class);
	
	public SshWebsocketServer(int webPort, final ReadyFactory readyFactory, final Address telnetAddress) throws IOException {
		final Queue queue = new Queue();
		final SshClient client = new SshClient(new SshClientConfigurator(queue).withAddress(telnetAddress).withLogin("dummy").withPassword("dummy"));
		new HttpServer(new HttpServerConfigurator(queue).withPort(webPort), new HttpServerHandlerFactory() {
			@Override
			public void failed(IOException e) {
			}
			@Override
			public void closed() {
			}
			
			@Override
			public HttpServerHandler create() {
				return new PatternDispatchHttpServerHandler()
				.add(Pattern.compile(".*\\.html"), new JsonDirectoryHttpServerHandler(new File(".")))
				.add(Pattern.compile(".*"), new WebsocketHttpServerHandler(new ReadyConnection() {
					@Override
					public void failed(IOException e) {
						LOGGER.debug("Failed from web", e);
						if (innerTelnetWrite != null) {
							innerTelnetWrite.failed(e);
						}
					}
					
					@Override
					public void close() {
						LOGGER.debug("Closed from web");
						if (innerTelnetWrite != null) {
							innerTelnetWrite.close();
						}
					}
					
					private CloseableByteBufferHandler innerWebWrite;
					private FailableCloseableByteBufferHandler innerTelnetWrite = null;

					@Override
					public void handle(Address address, ByteBuffer fromWebBuffer) {
						LOGGER.debug("Received from web {} bytes: {}", fromWebBuffer.remaining(), new String(fromWebBuffer.array(), fromWebBuffer.position(), fromWebBuffer.remaining(), Charsets.UTF_8));
						if (innerTelnetWrite != null) {
							LOGGER.debug("Sent to telnet");
							innerTelnetWrite.handle(address, fromWebBuffer);
						}
					}
					
					@Override
					public void connected(FailableCloseableByteBufferHandler webWrite) {
						innerWebWrite = webWrite;

						client.connect(new ReadyConnection() {
							@Override
							public void connected(FailableCloseableByteBufferHandler telnetWrite) {
								LOGGER.debug("Telnet connected");
								innerTelnetWrite = telnetWrite;
							}
							
							@Override
							public void handle(Address address, ByteBuffer fromTelnetBuffer) {
								LOGGER.debug("Received from telnet {} bytes: {}", fromTelnetBuffer.remaining(), new String(fromTelnetBuffer.array(), fromTelnetBuffer.position(), fromTelnetBuffer.remaining(), Charsets.UTF_8));
								innerWebWrite.handle(address, fromTelnetBuffer);
							}
							
							@Override
							public void close() {
								LOGGER.debug("Closed from telnet");
								innerWebWrite.close();
							}
							
							@Override
							public void failed(IOException e) {
								LOGGER.debug("Failed from telnet", e);
								innerWebWrite.close();
							}
						});
					}
				}));
			}
		});
	}
	
	public static void main(String[] args) throws Exception {
		new SshWebsocketServer(8080, new SocketReadyFactory(), new Address("w.davfx.com", SshClientConfigurator.DEFAULT_PORT));
	}
}
