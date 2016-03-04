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
	
	static interface Connect extends Sender {
		void connect(Connecting connecting, Closing closing, Failing failing, Receiver receiver);
		void disconnect();
	}
	
	private final Executor executor;
	private final Connect connect;
	public SimpleConnector(Executor executor, Connect connect) {
		this.executor = executor;
		this.connect = connect;
	}
	@Override
	public void connecting(Connecting connecting) {
		this.connecting = connecting;
	}
	@Override
	public void closing(Closing closing) {
		this.closing = closing;
	}
	@Override
	public void failing(Failing failing) {
		this.failing = failing;
	}
	@Override
	public void receiving(Receiver receiver) {
		this.receiver = receiver;
	}
	@Override
	public void connect() {
		if (executor == null) {
			connect.connect(connecting, closing, failing, receiver);
			return;
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
	}
	@Override
	public void disconnect() {
		connect.disconnect();
	}
	
	@Override
	public void send(Address address, ByteBuffer buffer) {
		connect.send(address, buffer);
	}
}
