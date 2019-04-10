package com.davfx.ninio.snmp;

import java.io.IOException;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.util.SerialExecutor;

public final class SnmpSerialByAddress {
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpSerialByAddress.class);

	private static final class OidValue {
		public final Oid oid;
		public final String value;
		public OidValue(Oid oid, String value) {
			this.oid = oid;
			this.value = value;
		}
	}
	private static final class ToSend {
		public final SnmpRequestBuilder wrappee;
		public final String community;
		public final AuthRemoteSpecification authRemoteSpecification;
		public final Oid oid;
		public final List<OidValue> added;
		public final SnmpCallType type;
		public final SnmpReceiver callback;
		public ToSend(SnmpRequestBuilder wrappee, String community, AuthRemoteSpecification authRemoteSpecification, Oid oid, List<OidValue> added, SnmpCallType type, SnmpReceiver callback) {
			this.wrappee = wrappee;
			this.community = community;
			this.authRemoteSpecification = authRemoteSpecification;
			this.oid = oid;
			this.added = added;
			this.type = type;
			this.callback = callback;
		}
	}
	private static final class ToSendByAddress {
		public final Deque<ToSend> list = new LinkedList<>();
		public Cancelable currentCancelable = null;
	}
	private final Map<Address, ToSendByAddress> toSend = new HashMap<>();
	private final SerialExecutor executor = new SerialExecutor(SnmpSerialByAddress.class);

	public SnmpSerialByAddress() {
	}
	
	public SnmpRequestBuilder wrap(final SnmpRequestBuilder wrappee) {
		return new SnmpRequestBuilder() {
			private String community;
			private AuthRemoteSpecification authRemoteSpecification;
			private Address address;
			private Oid oid;
			private final List<OidValue> added = new LinkedList<>();
			
			private ToSend currentToSend = null;
			
			@Override
			public SnmpRequestBuilder community(String community) {
				this.community = community;
				return this;
			}
			@Override
			public SnmpRequestBuilder auth(AuthRemoteSpecification authRemoteSpecification) {
				this.authRemoteSpecification = authRemoteSpecification;
				return this;
			}
			
			@Override
			public SnmpRequestBuilder build(Address address, Oid oid) {
				this.address = address;
				this.oid = oid;
				return this;
			}
			
			@Override
			public SnmpRequestBuilder add(Oid oid, String value) {
				this.added.add(new OidValue(oid, value));
				return this;
			}
			
			@Override
			public void cancel() {
				// Deprecated
				final Address a = address;
				executor.execute(new Runnable() {
					@Override
					public void run() {
						ToSend s = currentToSend;

						if (currentToSend == null) {
							return;
						}
						currentToSend = null;

						ToSendByAddress q = toSend.get(a);
						if (q == null) {
							// Nothing to do
						} else {
							if (!q.list.remove(s)) {
								q.currentCancelable.cancel();
								doSendNext(a, q);
							}
						}
					}
				});

			}
			
			private void doSendNext(final Address a, final ToSendByAddress q) {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						if (q.list.isEmpty()) {
							toSend.remove(a);
							return;
						}
						
						final ToSend s = q.list.removeFirst();
						if (s.community != null) {
							s.wrappee.community(s.community);
						}
						if (s.authRemoteSpecification != null) {
							s.wrappee.auth(s.authRemoteSpecification);
						}
						s.wrappee.build(a, s.oid);
						for (OidValue ov : s.added) {
							s.wrappee.add(ov.oid, ov.value);
						}
						
						q.currentCancelable = s.wrappee.call(s.type, new SnmpReceiver() {
							@Override
							public void received(SnmpResult result) {
								s.callback.received(result);
							}
							@Override
							public void finished() {
								q.currentCancelable = null;
								doSendNext(a, q);

								s.callback.finished();
							}
							@Override
							public void failed(IOException e) {
								q.currentCancelable = null;
								doSendNext(a, q);

								s.callback.failed(e);
							}
						});
					}
				});
			}
			
			@Override
			public Cancelable call(final SnmpCallType type, final SnmpReceiver callback) {
				final Address a = address;
				final ToSend s = new ToSend(wrappee, community, authRemoteSpecification, oid, added, type, callback);
				executor.execute(new Runnable() {
					@Override
					public void run() {
						if (currentToSend != null) {
							LOGGER.error("Invalid call!");
							return;
						}
						currentToSend = s;
						
						ToSendByAddress q = toSend.get(a);
						if (q == null) {
							LOGGER.info("Call to: {} NOT serialized", a);
							q = new ToSendByAddress();
							toSend.put(a, q);
							q.list.addLast(s);
							doSendNext(a, q);
						} else {
							LOGGER.info("Serializing call to: {}", a);
							q.list.addLast(s);
						}
					}
				});

				return new Cancelable() {
					@Override
					public void cancel() {
						executor.execute(new Runnable() {
							@Override
							public void run() {
								if (currentToSend == null) {
									return;
								}
								currentToSend = null;

								ToSendByAddress q = toSend.get(a);
								if (q == null) {
									// Nothing to do
								} else {
									if (!q.list.remove(s)) {
										q.currentCancelable.cancel();
										doSendNext(a, q);
									}
								}
							}
						});
					}
				};
			}
		};
	}
}
