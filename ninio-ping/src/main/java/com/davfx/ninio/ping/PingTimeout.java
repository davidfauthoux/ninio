package com.davfx.ninio.ping;

import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.Timeout;

public final class PingTimeout {
	private PingTimeout() {
	}
	
	public static PingRequestBuilder wrap(final Timeout t, final double timeout, final PingRequestBuilder wrappee, final Failing failing) {
		return new PingRequestBuilder() {
			private PingReceiver receiver = null;
			
			@Override
			public PingRequestBuilder receiving(PingReceiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
			@Override
			public PingRequest ping(String host) {
				final PingReceiver r = receiver;

				final Timeout.Manager m = t.set(timeout);
				m.run(failing);

				wrappee.receiving(new PingReceiver() {
					@Override
					public void received(double time) {
						m.cancel();
						if (r != null) {
							r.received(time);
						}
					}
				});

				wrappee.ping(host);
				
				return new PingRequest() {
					@Override
					public void cancel() {
						m.cancel();
					}
				};
			}
		};
	}
}
