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
				final SnmpRequestBuilder builder = wrappee.request();
				return new SnmpRequestBuilder() {
					@Override
					public SnmpRequestBuilder community(String community) {
						builder.community(community);
						return this;
					}
					@Override
					public SnmpRequestBuilder auth(AuthRemoteSpecification authRemoteSpecification) {
						builder.auth(authRemoteSpecification);
						return this;
					}
					
					private Timeout.Manager m;
					private Cancelable cancelable;

					@Override
					public SnmpRequestBuilderCancelable build(Address address, Oid oid) {
						m = t.set(timeout);
						
						final Cancelable c = builder.build(address, oid);

						cancelable = new Cancelable() {
							@Override
							public void cancel() {
								m.cancel();
								c.cancel();
							}
						};
						
						return new SnmpRequestBuilderCancelableImpl(this, cancelable);
					}

					@Override
					public Cancelable receive(final SnmpReceiver callback) {
						builder.receive(new SnmpReceiver() {
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
								callback.failed(new IOException("Timeout"));
							}
						});

						return cancelable;
					}
				};
			}
		};
	}
}
