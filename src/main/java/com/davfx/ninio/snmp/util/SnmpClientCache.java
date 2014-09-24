package com.davfx.ninio.snmp.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.SnmpClient;
import com.davfx.ninio.snmp.SnmpClientHandler;

public final class SnmpClientCache implements AutoCloseable {
	private final Queue queue;
	private final ScheduledExecutorService repeatExecutor = Executors.newSingleThreadScheduledExecutor();
	
	private static final class Hold {
		public final SnmpClient client;
		public final List<SnmpClientHandler> handlers = new LinkedList<>();
		public SnmpClientHandler.Callback launchedCallback;
		public Hold(SnmpClient client) {
			this.client = client;
		}		
	}
	
	private final Map<Address, Hold> clients = new HashMap<>();

	private double minTimeToRepeat = Double.NaN;
	private double repeatTime = Double.NaN;
	private double timeoutFromBeginning = Double.NaN;
	private double timeoutFromLastReception = Double.NaN;
	private ReadyFactory readyFactory = null;
	private int bulkSize = -1;
	private int getLimit = -1;

	public SnmpClientCache(Queue queue) {
		this.queue = queue;
	}

	public SnmpClientCache withMinTimeToRepeat(double minTimeToRepeat) {
		this.minTimeToRepeat = minTimeToRepeat;
		return this;
	}
	public SnmpClientCache withRepeatTime(double repeatTime) {
		this.repeatTime = repeatTime;
		return this;
	}

	public SnmpClientCache withTimeoutFromBeginning(double timeoutFromBeginning) {
		this.timeoutFromBeginning = timeoutFromBeginning;
		return this;
	}
	public SnmpClientCache withTimeoutFromLastReception(double timeoutFromLastReception) {
		this.timeoutFromLastReception = timeoutFromLastReception;
		return this;
	}

	public SnmpClientCache withBulkSize(int bulkSize) {
		this.bulkSize = bulkSize;
		return this;
	}
	public SnmpClientCache withGetLimit(int getLimit) {
		this.getLimit = getLimit;
		return this;
	}
	
	public SnmpClientCache override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
	
	public static interface Connectable {
		Connectable withCommunity(String community);
		Connectable withLoginPassword(String authLogin, String authPassword, String authDigestAlgorithm, String privLogin, String privPassword, String privEncryptionAlgorithm);
		void connect(SnmpClientHandler clientHandler);
	}
	
	public Connectable get(final Address address) {
		return new Connectable() {
			private String community = null;
			private String authLogin = null;
			private String authPassword = null;
			private String authDigestAlgorithm = null;
			private String privLogin = null;
			private String privPassword = null;
			private String privEncryptionAlgorithm = null;
			@Override
			public Connectable withCommunity(String community) {
				this.community = community;
				return this;
			}
			@Override
			public Connectable withLoginPassword(String authLogin, String authPassword, String authDigestAlgorithm, String privLogin, String privPassword, String privEncryptionAlgorithm) {
				this.authLogin = authLogin;
				this.authPassword = authPassword;
				this.authDigestAlgorithm = authDigestAlgorithm;
				this.privLogin = privLogin;
				this.privPassword = privPassword;
				this.privEncryptionAlgorithm = privEncryptionAlgorithm;
				return this;
			}
			@Override
			public void connect(SnmpClientHandler clientHandler) {
				Hold c = clients.get(address);
				if (c == null) {
					SnmpClient snmpClient = new SnmpClient();
					if (community != null) {
						snmpClient.withCommunity(community);
					}
					if ((authLogin != null) && (authPassword != null) && (authDigestAlgorithm != null) && (privLogin != null) && (privPassword != null) && (privEncryptionAlgorithm != null)) {
						snmpClient.withLoginPassword(authLogin, authPassword, authDigestAlgorithm, privLogin, privPassword, privEncryptionAlgorithm);
					}

					if (bulkSize >= 0) {
						snmpClient.withBulkSize(bulkSize);
					}
					if (getLimit >= 0) {
						snmpClient.withBulkSize(getLimit);
					}
					if (!Double.isNaN(minTimeToRepeat)) {
						snmpClient.withMinTimeToRepeat(minTimeToRepeat);
					}
					if (!Double.isNaN(repeatTime)) {
						snmpClient.withRepeatTime(repeatTime);
					}
					if (!Double.isNaN(timeoutFromBeginning)) {
						snmpClient.withTimeoutFromBeginning(timeoutFromBeginning);
					}
					if (!Double.isNaN(timeoutFromLastReception)) {
						snmpClient.withTimeoutFromLastReception(timeoutFromLastReception);
					}
					if (readyFactory != null) {
						snmpClient.override(readyFactory);
					}
					c = new Hold(snmpClient.withAddress(address).withQueue(queue, repeatExecutor));
					
					final Hold cc = c;
					clients.put(address, cc);
					c.handlers.add(clientHandler);
					
					c.client.connect(new SnmpClientHandler() {
						@Override
						public void failed(IOException e) {
							clients.remove(address);
							for (SnmpClientHandler h : cc.handlers) {
								h.failed(e);
							}
						}
						@Override
						public void close() {
							clients.remove(address);
							for (SnmpClientHandler h : cc.handlers) {
								h.close();
							}
						}
						@Override
						public void launched(final Callback callback) {
							cc.launchedCallback = callback;
							for (SnmpClientHandler h : cc.handlers) {
								h.launched(new Callback() {
									@Override
									public void close() {
										// Never actually closed
									}
									@Override
									public void get(Oid oid, GetCallback getCallback) {
										callback.get(oid, getCallback);
									}
								});
							}
						}
					});
				} else {
					final Hold cc = c;
					
					cc.handlers.add(clientHandler);
					if (cc.launchedCallback != null) {
						clientHandler.launched(new SnmpClientHandler.Callback() {
							@Override
							public void close() {
								// Never actually closed
							}
							@Override
							public void get(Oid oid, GetCallback getCallback) {
								cc.launchedCallback.get(oid, getCallback);
							}
						});
					}
				}
			}
		};
	}
	
	@Override
	public void close() {
		repeatExecutor.shutdown();
		// Queue not closed here but by caller
		
		for(Hold c : clients.values()) {
			if (c.launchedCallback != null) {
				c.launchedCallback.close();
			}
			c.handlers.clear();
		}
	}
}
