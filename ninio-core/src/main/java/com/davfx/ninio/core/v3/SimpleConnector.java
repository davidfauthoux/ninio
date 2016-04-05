package com.davfx.ninio.core.v3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import com.davfx.ninio.core.Address;

final class SimpleConnector implements Connector {
	Connecting connecting = null;
	Closing closing = null;
	Failing failing = null;
	Receiver receiver = null;
	
	static interface Connect {
		void connect(Connecting connecting, Closing closing, Failing failing, Receiver receiver);
		void disconnect();
		void send(Address address, ByteBuffer buffer);
	}
	
	private final Executor executor;
	private final Connect connect;
	public SimpleConnector(Executor executor, Connect connect) {
		this.executor = executor;
		this.connect = connect;
	}
	@Override
	public Connector connecting(Connecting connecting) {
		this.connecting = connecting;
		return this;
	}
	@Override
	public Connector closing(Closing closing) {
		this.closing = closing;
		return this;
	}
	@Override
	public Connector failing(Failing failing) {
		this.failing = failing;
		return this;
	}
	@Override
	public Connector receiving(Receiver receiver) {
		this.receiver = receiver;
		return this;
	}
	@Override
	public Connector connect() {
		if (executor == null) {
			connect.connect(connecting, closing, failing, receiver);
			return this;
		}
		final Connecting co = connecting;
		final Closing cl = closing;
		final Failing f = failing;
		final Receiver r = receiver;
		connect.connect(new Connecting() {
			@Override
			public void connected() {
				if (co == null) {
					return;
				}
				executor.execute(new Runnable() {
					@Override
					public void run() {
						co.connected();
					}
				});
			}
		}, new Closing() {
			@Override
			public void closed() {
				if (cl == null) {
					return;
				}
				executor.execute(new Runnable() {
					@Override
					public void run() {
						cl.closed();
					}
				});
			}
		}, new Failing() {
			@Override
			public void failed(final IOException e) {
				if (f == null) {
					return;
				}
				executor.execute(new Runnable() {
					@Override
					public void run() {
						f.failed(e);
					}
				});
			}
		}, new Receiver() {
			@Override
			public void received(final Address address, final ByteBuffer buffer) {
				if (r == null) {
					return;
				}
				executor.execute(new Runnable() {
					@Override
					public void run() {
						r.received(address, buffer);
					}
				});
			}
		});
		return this;
	}
	@Override
	public Connector disconnect() {
		connect.disconnect();
		return this;
	}
	
	@Override
	public Connector send(Address address, ByteBuffer buffer) {
		connect.send(address, buffer);
		return this;
	}
}
