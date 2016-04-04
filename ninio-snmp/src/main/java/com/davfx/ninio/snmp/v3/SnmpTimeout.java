package com.davfx.ninio.snmp.v3;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.snmp.AuthRemoteSpecification;
import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.Result;

public final class SnmpTimeout {
	private SnmpTimeout() {
	}
	
	private static final class CancelableSnmpReceiver implements SnmpReceiver {
		private final SnmpReceiver wrappee;
		public boolean canceled = false;
		public CancelableSnmpReceiver(SnmpReceiver wrappee) {
			this.wrappee = wrappee;
		}
		@Override
		public void received(Result result) {
			if (canceled) {
				return;
			}
			if (wrappee == null) {
				return;
			}
			wrappee.received(result);
		}
		@Override
		public void finished() {
			if (canceled) {
				return;
			}
			if (wrappee == null) {
				return;
			}
			wrappee.finished();
		}
	}
	
	private static final class CancelableFailing implements Failing {
		private final Failing wrappee;
		public boolean canceled = false;
		public CancelableFailing(Failing wrappee) {
			this.wrappee = wrappee;
		}
		@Override
		public void failed(IOException e) {
			if (canceled) {
				return;
			}
			if (wrappee == null) {
				return;
			}
			wrappee.failed(e);
		}
	}
	
	// !! executor MUST be the same as the one used in SnmpClient
	public static SnmpRequest hook(final ScheduledExecutorService executor, final SnmpRequest request, final double timeout) {
		return new SnmpRequest() {
			private SnmpReceiver receiver = null;
			private Failing failing = null;
			private ScheduledFuture<?> future = null;

			@Override
			public SnmpRequest receiving(SnmpReceiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
			@Override
			public SnmpRequest failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public SnmpRequest get(Address address, String community, AuthRemoteSpecification authRemoteSpecification, Oid oid) {
				final CancelableSnmpReceiver r = new CancelableSnmpReceiver(receiver);
				final CancelableFailing f = new CancelableFailing(failing);

				final Runnable schedule = new Runnable() {
					@Override
					public void run() {
						future = executor.schedule(new Runnable() {
							@Override
							public void run() {
								f.failed(new IOException("Timeout"));
								r.canceled = true;
								f.canceled = true;
								future = null;
							}
						}, (long) (timeout * 1000d), TimeUnit.MILLISECONDS);
					}
				};
				
				final Runnable cancel = new Runnable() {
					@Override
					public void run() {
						if (future != null) {
							future.cancel(false);
						}
						future = null;
					}
				};
				
				request.failing(new Failing() {
					@Override
					public void failed(IOException e) {
						cancel.run();
						f.failed(e);
					}
				});

				request.receiving(new SnmpReceiver() {
					@Override
					public void received(Result result) {
						schedule.run();
						r.received(result);
					}
					@Override
					public void finished() {
						cancel.run();
						r.finished();
					}
				});
				
				schedule.run();
				request.get(address, community, authRemoteSpecification, oid);
				return this;
			}
		};
	}
}
