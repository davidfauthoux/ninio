package com.davfx.ninio.ping;

import java.io.IOException;

import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

public final class Ping {
	
	private static final Config CONFIG = ConfigFactory.load(Ping.class.getClassLoader());
	private static SyncPing DEFAULT_SYNC_PING;
	static {
		String mode = CONFIG.getString("ninio.ping.mode");
		if (mode.equals("java")) {
			DEFAULT_SYNC_PING = new PureJavaSyncPing();
		} else if (mode.equals("shell")) {
			DEFAULT_SYNC_PING = new ShellCommandSyncPing();
		} else {
			throw new ConfigException.BadValue("ninio.ping.mode", "Invalid mode, only 'java' and 'shell' allowed");
		}
	}

	private static final Queue DEFAULT_QUEUE = new Queue();

	private Queue queue = DEFAULT_QUEUE;
	private ReadyFactory readyFactory = null;

	public Ping() {
	}

	public Ping withQueue(Queue queue) {
		this.queue = queue;
		return this;
	}
	
	public Ping override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
	
	public PingClient client() {
		return new PingClient(queue, (readyFactory == null) ? new InternalPingServerReadyFactory(queue, DEFAULT_SYNC_PING) : readyFactory);
	}
	
	public void ping(final String host, final PingClientHandler.Callback.PingCallback pingCallback) {
		final PingClient client = client();
		client.connect(new PingClientHandler() {
			@Override
			public void failed(IOException e) {
				pingCallback.failed(e);
			}
			@Override
			public void close() {
				pingCallback.failed(new IOException("Prematurely closed"));
			}
			@Override
			public void launched(final Callback callback) {
				callback.ping(host, new Callback.PingCallback() {
					@Override
					public void failed(IOException e) {
						callback.close();
						client.close();
						pingCallback.failed(e);
					}
					@Override
					public void pong(double time) {
						callback.close();
						client.close();
						pingCallback.pong(time);
					}
				});
			}
		});
	}
}
