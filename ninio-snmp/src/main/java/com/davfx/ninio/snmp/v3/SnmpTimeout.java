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
	
	private static final class Cancelable {
		public boolean canceled = false;
		public ScheduledFuture<?> future = null;
		public Cancelable() {
		}
	}
	
	// !! executor MUST be the same as the one used in SnmpClient
	public static SnmpReceiverRequestBuilder hook(final ScheduledExecutorService executor, final SnmpReceiverRequestBuilder request, final double timeout) {
		return new SnmpReceiverRequestBuilder() {
			private SnmpReceiver receiver = null;
			private Failing failing = null;

			@Override
			public SnmpReceiverRequestBuilder receiving(SnmpReceiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
			@Override
			public SnmpReceiverRequestBuilder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public SnmpReceiverRequest build() {
				final SnmpReceiver thisReceiver = receiver;
				final Failing thisFailing = failing;

				final Cancelable cancelable = new Cancelable();

				final Runnable schedule = new Runnable() {
					@Override
					public void run() {
						cancelable.future = executor.schedule(new Runnable() {
							@Override
							public void run() {
								cancelable.future = null;
								
								if (!cancelable.canceled) {
									cancelable.canceled = true;
									thisFailing.failed(new IOException("Timeout"));
								}
							}
						}, (long) (timeout * 1000d), TimeUnit.MILLISECONDS);
					}
				};
				
				final Runnable cancel = new Runnable() {
					@Override
					public void run() {
						if (cancelable.future != null) {
							cancelable.future.cancel(false);
							cancelable.future = null;
						}
					}
				};
				
				request.failing(new Failing() {
					@Override
					public void failed(final IOException e) {
						executor.execute(new Runnable() {
							@Override
							public void run() {
								cancel.run();
								if (cancelable.canceled) {
									return;
								}
								thisFailing.failed(e);
							}
						});
					}
				});

				request.receiving(new SnmpReceiver() {
					@Override
					public void received(final Result result) {
						executor.execute(new Runnable() {
							@Override
							public void run() {
								cancel.run();
								schedule.run();
								
								if (cancelable.canceled) {
									return;
								}
								thisReceiver.received(result);
							}
						});
					}
					@Override
					public void finished() {
						executor.execute(new Runnable() {
							@Override
							public void run() {
								cancel.run();
								if (cancelable.canceled) {
									return;
								}
								thisReceiver.finished();
							}
						});
					}
				});
				
				schedule.run();

				final SnmpReceiverRequest r = request.build();
				
				return new SnmpReceiverRequest() {
					@Override
					public void get(Address address, String community, AuthRemoteSpecification authRemoteSpecification, Oid oid) {
						r.get(address, community, authRemoteSpecification, oid);
					}
				};
			}
		};
	}
}
