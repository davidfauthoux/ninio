package com.davfx.ninio.ping;

import java.io.IOException;

import com.davfx.ninio.core.Timeout;

public final class PingTimeout {
	private PingTimeout() {
	}
	
	public static PingConnecter wrap(final double timeout, final PingConnecter wrappee) {
		final Timeout t = new Timeout();
		return new PingConnecter() {
			@Override
			public void close() {
				t.close();
				wrappee.close();
			}
			
			@Override
			public void connect(PingConnection callback) {
				wrappee.connect(callback);
			}
			
			@Override
			public Cancelable ping(byte[] ip, PingReceiver receiver) {
				return PingTimeout.ping(t, timeout, ip, wrappee, receiver);
			}
		};
	}
	
	public static Cancelable ping(Timeout t, double timeout, byte[] ip, PingConnecter wrappee, final PingReceiver receiver) {
		final Timeout.Manager m = t.set(timeout);

		final Cancelable cancelable = wrappee.ping(ip, new PingReceiver() {
			@Override
			public void received(double time) {
				m.cancel();
				receiver.received(time);
			}
			@Override
			public void failed(IOException ioe) {
				m.cancel();
				receiver.failed(ioe);
			}
		});
		
		m.run(new Runnable() {
			@Override
			public void run() {
				cancelable.cancel();
				receiver.failed(new IOException("Timeout"));
			}
		});

		return new Cancelable() {
			@Override
			public void cancel() {
				m.cancel();
				cancelable.cancel();
			}
		};
	}
}
