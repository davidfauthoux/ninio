package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.SerialExecutor;
import com.typesafe.config.Config;

public final class ReconnectConnecter implements Connecter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ReconnectConnecter.class);
	
	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(Timeout.class.getPackage().getName());
	private static final double SLEEP = ConfigUtils.getDuration(CONFIG, "reconnect.sleep");

	public static interface Builder extends NinioBuilder<Connecter> {
		Builder with(NinioBuilder<Connecter> builder);
	}

	public static Builder builder() {
		return new Builder() {
			private NinioBuilder<Connecter> builder = null;
			
			@Override
			public Builder with(NinioBuilder<Connecter> builder) {
				this.builder = builder;
				return this;
			}

			@Override
			public Connecter create(Queue queue) {
				if (builder == null) {
					throw new NullPointerException("builder");
				}
				return new ReconnectConnecter(queue, builder);
			}
		};
	}
	

	
	private final Queue queue;
	private final NinioBuilder<Connecter> builder;
	private final Executor executor = new SerialExecutor(ReconnectConnecter.class);
	private boolean closed = false;
	private Connecter connecter = null;

	private ReconnectConnecter(Queue queue, NinioBuilder<Connecter> builder) {
		this.queue = queue;
		this.builder = builder;
	}
	
	@Override
	public void connect(Connection callback) {
		connect(callback, false);
	}
	
	private void connect(final Connection callback, final boolean sleep) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				if (closed) {
					return;
				}
				
				connecter = builder.create(queue);
				connecter.connect(new Connection() {
					@Override
					public void received(Address address, ByteBuffer buffer) {
						callback.received(address, buffer);
					}
					
					@Override
					public void connected(Address address) {
						callback.connected(address);
					}
					
					@Override
					public void closed() {
						callback.closed();
						reconnect(callback);
					}
					
					@Override
					public void failed(IOException e) {
						callback.failed(e);
						reconnect(callback);
					}
				});
				
				if (sleep) {
					LOGGER.trace("Sleeping");
					try {
						Thread.sleep((long) (SLEEP * 1000d));
					} catch (InterruptedException ie) {
					}
				}
			}
		});
	}
	
	private void reconnect(final Connection callback) {
		LOGGER.trace("Will reconnect");
		executor.execute(new Runnable() {
			@Override
			public void run() {
				connect(callback, true);
			}
		});
	}
	
	@Override
	public void close() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				closed = true;
				if (connecter != null) {
					connecter.close();
					connecter = null;
				}
			}
		});
	}
	
	@Override
	public void send(final Address address, final ByteBuffer buffer, final SendCallback callback) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				connecter.send(address, buffer, callback);
			}
		});
	}
}
