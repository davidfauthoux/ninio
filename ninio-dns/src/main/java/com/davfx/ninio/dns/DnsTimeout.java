package com.davfx.ninio.dns;

import java.io.IOException;

import com.davfx.ninio.core.Timeout;

public final class DnsTimeout {
	private DnsTimeout() {
	}
	
	public static DnsConnecter wrap(final double timeout, final DnsConnecter wrappee) {
		final Timeout t = new Timeout();
		return new DnsConnecter() {
			@Override
			public void close() {
				t.close();
				wrappee.close();
			}
			
			@Override
			public Cancelable resolve(String host, final DnsReceiver receiver) {
				final Timeout.Manager m = t.set(timeout);
				m.run(new Runnable() {
					@Override
					public void run() {
						receiver.failed(new IOException("Timeout"));
					}
				});

				final Cancelable cancelable = wrappee.resolve(host, new DnsReceiver() {
					@Override
					public void received(byte[] ip) {
						m.cancel();
						receiver.received(ip);
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
