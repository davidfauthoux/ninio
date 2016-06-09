package com.davfx.ninio.snmp.v3;

import java.io.IOException;

import com.davfx.ninio.core.v3.Address;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.Timeout;

public final class SnmpTimeout {
	private SnmpTimeout() {
	}
	
	public static SnmpRequestBuilder wrap(final Timeout t, final double timeout, final SnmpRequestBuilder wrappee) {
		return new SnmpRequestBuilder() {
			private SnmpReceiver receiver = null;
			private Failing failing = null;
			
			@Override
			public SnmpRequestBuilder receiving(SnmpReceiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
			@Override
			public SnmpRequestBuilder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public SnmpRequest get(Address address, String community, AuthRemoteSpecification authRemoteSpecification, Oid oid) {
				final SnmpReceiver r = receiver;
				final Failing f = failing;

				final Timeout.Manager m = t.set(timeout);
				m.run(failing);

				wrappee.failing(new Failing() {
					@Override
					public void failed(IOException e) {
						m.cancel();
						if (f != null) {
							f.failed(e);
						}
					}
				});
				
				wrappee.receiving(new SnmpReceiver() {
					@Override
					public void received(SnmpResult result) {
						m.reset();
						if (r != null) {
							r.received(result);
						}
					}
					@Override
					public void finished() {
						m.cancel();
						if (r != null) {
							r.finished();
						}
					}
				});

				wrappee.get(address, community, authRemoteSpecification, oid);
				
				return new SnmpRequest() {
					@Override
					public void cancel() {
						m.cancel();
					}
				};
			}
		};
	}
}
