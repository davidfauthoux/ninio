package com.davfx.ninio.http.websocket.util;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.http.HttpServer;
import com.davfx.ninio.http.HttpServerConfigurator;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;
import com.davfx.ninio.http.websocket.WebsocketHttpServerHandler;
import com.davfx.ninio.ssh.SshClient;
import com.davfx.ninio.ssh.SshClientConfigurator;
import com.google.common.base.Charsets;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class SshWebsocketServer {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SshWebsocketServer.class);
	
	private static final Config CONFIG = ConfigFactory.load();

	public static void main(String[] args) throws Exception {
		Queue queue = new Queue();
		new SshWebsocketServer(
			new HttpServerConfigurator(queue).withPort(CONFIG.getInt("http.sshwebsocket.web.port")),
			new SshClientConfigurator(queue).withAddress(new Address(CONFIG.getString("http.sshwebsocket.ssh.host"), CONFIG.getInt("http.sshwebsocket.ssh.port"))).withLogin(CONFIG.getString("http.sshwebsocket.ssh.login")).withPassword(CONFIG.getString("http.sshwebsocket.ssh.password"))
			// CONFIG.getString("http.sshwebsocket.path")
		);
	}
	
	
	public SshWebsocketServer(HttpServerConfigurator httpConfigurator, SshClientConfigurator sshConfigurator) { // , final String websocketPath) {
		final SshClient client = new SshClient(sshConfigurator);
		new HttpServer(httpConfigurator, new HttpServerHandlerFactory() {
			@Override
			public void failed(IOException e) {
			}
			@Override
			public void closed() {
			}
			
			@Override
			public HttpServerHandler create() {
				// return new PatternDispatchHttpServerHandler()
				// .add(Pattern.compile(websocketPath), 
				return new WebsocketHttpServerHandler(new ReadyConnection() {
						@Override
						public void failed(IOException e) {
							LOGGER.debug("Failed from web", e);
							if (innerSshWrite != null) {
								innerSshWrite.failed(e);
							}
						}
						
						@Override
						public void close() {
							LOGGER.debug("Closed from web");
							if (innerSshWrite != null) {
								innerSshWrite.close();
							}
						}
						
						private CloseableByteBufferHandler innerWebWrite;
						private FailableCloseableByteBufferHandler innerSshWrite = null;
	
						@Override
						public void handle(Address address, ByteBuffer fromWebBuffer) {
							LOGGER.debug("Received from web {} bytes: {}", fromWebBuffer.remaining(), new String(fromWebBuffer.array(), fromWebBuffer.position(), fromWebBuffer.remaining(), Charsets.UTF_8));
							if (innerSshWrite != null) {
								innerSshWrite.handle(address, fromWebBuffer);
							}
						}
						
						@Override
						public void connected(FailableCloseableByteBufferHandler webWrite) {
							innerWebWrite = webWrite;
	
							client.connect(new ReadyConnection() {
								@Override
								public void connected(FailableCloseableByteBufferHandler sshWrite) {
									LOGGER.debug("Ssh connected");
									innerSshWrite = sshWrite;
								}
								
								@Override
								public void handle(Address address, ByteBuffer fromSshBuffer) {
									LOGGER.debug("Received from ssh {} bytes: {}", fromSshBuffer.remaining(), new String(fromSshBuffer.array(), fromSshBuffer.position(), fromSshBuffer.remaining(), Charsets.UTF_8));
									innerWebWrite.handle(address, fromSshBuffer);
								}
								
								@Override
								public void close() {
									LOGGER.debug("Closed from ssh");
									innerWebWrite.close();
								}
								
								@Override
								public void failed(IOException e) {
									LOGGER.debug("Failed from ssh", e);
									innerWebWrite.close();
								}
							});
						}
					}); //)
				// .add(Pattern.compile(".*"), new JsonDirectoryHttpServerHandler(new File(".")));
			}
		});
	}
}
