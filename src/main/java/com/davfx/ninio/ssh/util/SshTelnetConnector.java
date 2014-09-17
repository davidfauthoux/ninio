package com.davfx.ninio.ssh.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.common.SocketReadyFactory;
import com.davfx.ninio.ssh.SshClient;
import com.davfx.ninio.telnet.TelnetClientHandler;
import com.davfx.ninio.telnet.TelnetConnector;
import com.google.common.base.Charsets;

public final class SshTelnetConnector implements TelnetConnector {
	private static final Logger LOGGER = LoggerFactory.getLogger(SshTelnetConnector.class);
	
	private Queue queue = null;
	private Address address = null;
	private String host = null;
	private int port = -1;
	
	private ReadyFactory readyFactory = new SocketReadyFactory();

	public SshTelnetConnector() {
	}
	
	@Override
	public String getEol() {
		return SshClient.EOL;
	}
	
	@Override
	public SshTelnetConnector withQueue(Queue queue) {
		this.queue = queue;
		return this;
	}
	
	@Override
	public SshTelnetConnector withHost(String host) {
		this.host = host;
		return this;
	}
	@Override
	public SshTelnetConnector withPort(int port) {
		this.port = port;
		return this;
	}
	@Override
	public SshTelnetConnector withAddress(Address address) {
		this.address = address;
		return this;
	}
	
	@Override
	public SshTelnetConnector override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
	
	@Override
	public void connect(TelnetClientHandler clientHandler) {
		clientHandler.launched(new TelnetClientHandler.Callback() {
			private String login = null;
			private String password = null;
			private SshClient client = null;
			private CloseableByteBufferHandler launchedCallback;
			private final List<String> toSend = new LinkedList<>();
			
			@Override
			public void close() {
				if (launchedCallback != null) {
					launchedCallback.close();
				}
			}
			
			@Override
			public void send(String text) {
				if (login == null) {
					login = text.substring(0, text.length() - SshClient.EOL.length());
					clientHandler.received(SshClient.EOL);
					return;
				}
				
				if (password == null) {
					password = text.substring(0, text.length() - SshClient.EOL.length());
					clientHandler.received(SshClient.EOL);
				} else {
					if (launchedCallback == null) {
						toSend.add(text);
					} else {
						launchedCallback.handle(null, ByteBuffer.wrap(text.getBytes(Charsets.UTF_8)));
					}
				}

				if (client == null) {
					
					client = new SshClient().withLogin(login).withPassword(password);
					if (host != null) {
						client.withHost(host);
					}
					if (port >= 0) {
						client.withPort(port);
					}
					if (address != null) {
						client.withAddress(address);
					}
					if (queue != null) {
						client.withQueue(queue);
					}
					if (readyFactory != null) {
						client.override(readyFactory);
					}
	
					client.connect(new ReadyConnection() {
						@Override
						public void failed(IOException e) {
							LOGGER.error("Failed", e);
							clientHandler.close();
						}
						
						@Override
						public void close() {
							clientHandler.close();
						}
						
						@Override
						public void handle(Address address, ByteBuffer buffer) {
							byte[] b = new byte[buffer.remaining()];
							buffer.get(b);
							clientHandler.received(new String(b, Charsets.UTF_8));
						}
						
						@Override
						public void connected(FailableCloseableByteBufferHandler callback) {
							launchedCallback = callback;
							for (String s : toSend) {
								callback.handle(null, ByteBuffer.wrap(s.getBytes(Charsets.UTF_8)));
							}
							toSend.clear();
						}
					});
					
				}
			}
		});
	}
}
