package com.davfx.ninio.ping;

import java.io.IOException;

import com.davfx.ninio.core.Timeout;

public final class PingTimeout {
	private PingTimeout() {
	}
	
	public static PingConnecter.Connecting wrap(final double timeout, final PingConnecter.Connecting wrappee) {
		final Timeout t = new Timeout();
		return new PingConnecter.Connecting() {
			@Override
			public void close() {
				t.close();
				wrappee.close();
			}
			
			@Override
			public Cancelable ping(String host, final Callback receiver) {
				final Timeout.Manager m = t.set(timeout);
				m.run(new Runnable() {
					@Override
					public void run() {
						receiver.failed(new IOException("Timeout"));
					}
				});

				final Cancelable cancelable = wrappee.ping(host, new PingConnecter.Connecting.Callback() {
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
				
				return new Cancelable() {
					@Override
					public void cancel() {
						m.cancel();
						cancelable.cancel();
					}
				};
			}
		};
	}
}
