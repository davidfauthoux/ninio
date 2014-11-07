package com.davfx.ninio.remote.ssh;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.remote.RemoteClientHandler;
import com.davfx.ninio.remote.RemoteConnector;
import com.davfx.ninio.ssh.SshClient;
import com.davfx.ninio.ssh.SshClientConfigurator;
import com.davfx.ninio.telnet.TelnetClient;
import com.google.common.base.Charsets;

public final class SshRemoteConnector implements RemoteConnector {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SshRemoteConnector.class);
	
	private final SshClientConfigurator configurator;
	private SshClient client = null;
	
	public SshRemoteConnector(SshClientConfigurator configurator) {
		this.configurator = configurator;
	}
	
	@Override
	public void close() {
	}
	
	@Override
	public String getEol() {
		return TelnetClient.EOL;
	}
	
	@Override
	public void connect(final RemoteClientHandler clientHandler) {
		clientHandler.launched(new RemoteClientHandler.Callback() {
			private String login = null;
			private String password = null;
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
					
					client = new SshClient(new SshClientConfigurator(configurator).withLogin(login).withPassword(password));
	
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
