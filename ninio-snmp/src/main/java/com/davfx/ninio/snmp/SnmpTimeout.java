package com.davfx.ninio.snmp;

import java.io.IOException;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Timeout;

public final class SnmpTimeout {
	private SnmpTimeout() {
	}
	
	public static SnmpConnecter wrap(final double timeout, final SnmpConnecter wrappee) {
		final Timeout t = new Timeout();
		return new SnmpConnecter() {
			@Override
			public void close() {
				t.close();
				wrappee.close();
			}
			
			@Override
			public void connect(SnmpConnection callback) {
				wrappee.connect(callback);
			}
			
			@Override
			public SnmpRequestBuilder request() {
				return wrap(t, timeout, wrappee.request());
			}
		};
	}
	

	public static SnmpRequestBuilder wrap(final Timeout t, final double timeout, final SnmpRequestBuilder wrappee) {
		return new SnmpRequestBuilder() {
			@Override
			public SnmpRequestBuilder community(String community) {
				wrappee.community(community);
				return this;
			}
			@Override
			public SnmpRequestBuilder auth(AuthRemoteSpecification authRemoteSpecification) {
				wrappee.auth(authRemoteSpecification);
				return this;
			}
			
			@Override
			public SnmpRequestBuilder build(Address address, Oid oid) {
				wrappee.build(address, oid);
				return this;
			}
			
			@Override
			public SnmpRequestBuilder add(Oid oid, String value) {
				wrappee.add(oid, value);
				return this;
			}
			
			@Override
			public void cancel() {
				// Deprecated
				wrappee.cancel();
			}

			@Override
			public Cancelable call(SnmpCallType type, final SnmpReceiver callback) {
				final Timeout.Manager m = t.set(timeout);
				wrappee.call(type, new SnmpReceiver() {
					@Override
					public void failed(IOException ioe) {
						m.cancel();
						callback.failed(ioe);
					}
					
					@Override
					public void received(SnmpResult result) {
						m.reset();
						callback.received(result);
					}
					@Override
					public void finished() {
						m.cancel();
						callback.finished();
					}
				});

				m.run(new Runnable() {
					@Override
					public void run() {
						wrappee.cancel(); // Deprecated
						callback.failed(new IOException("Timeout"));
					}
				});

				return new Cancelable() {
					@Override
					public void cancel() {
						m.cancel();
						wrappee.cancel(); // Deprecated
					}
				};
			}
		};
	}
}
