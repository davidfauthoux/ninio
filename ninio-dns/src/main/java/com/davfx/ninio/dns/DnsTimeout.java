package com.davfx.ninio.dns;

import java.io.IOException;
import java.net.ProtocolFamily;

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
			public void connect(DnsConnection callback) {
				wrappee.connect(callback);
			}
			
			@Override
			public DnsRequestBuilder request() {
				return wrap(t, timeout, wrappee.request());
			}
		};
	}
	
	public static DnsRequestBuilder wrap(final Timeout t, final double timeout, final DnsRequestBuilder wrappee) {
		return new DnsRequestBuilder() {
			private Timeout.Manager m;
			private Cancelable cancelable;

			@Override
			public SnmpRequestBuilderCancelable resolve(String host, ProtocolFamily family) {
				m = t.set(timeout);
				
				final Cancelable c = wrappee.resolve(host, family);

				cancelable = new Cancelable() {
					@Override
					public void cancel() {
						m.cancel();
						c.cancel();
					}
				};
				
				return new DnsRequestBuilderCancelableImpl(this, cancelable);
			}

			@Override
			public Cancelable receive(final DnsReceiver callback) {
				wrappee.receive(new DnsReceiver() {
					@Override
					public void failed(IOException ioe) {
						m.cancel();
						callback.failed(ioe);
					}
					
					@Override
					public void received(byte[] ip) {
						m.cancel();
						callback.received(ip);
					}
				});

				m.run(new Runnable() {
					@Override
					public void run() {
						callback.failed(new IOException("Timeout"));
					}
				});

				return cancelable;
			}
		};
	}
}
