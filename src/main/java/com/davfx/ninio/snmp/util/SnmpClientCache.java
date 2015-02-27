package com.davfx.ninio.snmp.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.SnmpClient;
import com.davfx.ninio.snmp.SnmpClientConfigurator;
import com.davfx.ninio.snmp.SnmpClientHandler;
import com.davfx.util.ConfigUtils;

public final class SnmpClientCache implements AutoCloseable {
	private static final int CACHE_EXPIRE_THRESHOLD = ConfigUtils.load(SnmpClientCache.class).getInt("snmp.cache.expire.threshold");
	
	private final SnmpClientConfigurator configurator;
	
	private static final class Hold {
		public final SnmpClient client;
		public final List<SnmpClientHandler> handlers = new LinkedList<>();
		public SnmpClientHandler.Callback launchedCallback;
		public Hold(SnmpClient client) {
			this.client = client;
		}		
	}
	
	private final Map<Address, Hold> clients = new HashMap<>();

	public SnmpClientCache(SnmpClientConfigurator configurator) {
		this.configurator = configurator;
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
			public void connect(final SnmpClientHandler clientHandler) {
				if ((CACHE_EXPIRE_THRESHOLD > 0) && (clients.size() >= CACHE_EXPIRE_THRESHOLD)) {
					Iterator<Hold> i = clients.values().iterator();
					while (i.hasNext()) {
						Hold c = i.next();
						if (c.handlers.isEmpty()) {
							if (c.launchedCallback != null) {
								c.launchedCallback.close();
							}
							i.remove();
							c.client.close();
						}
					}
				}
				
				Hold c = clients.get(address);
				if (c == null) {
					SnmpClientConfigurator clientConfigurator = new SnmpClientConfigurator(configurator);
					if (community != null) {
						clientConfigurator.withCommunity(community);
					}
					if ((authLogin != null) && (authPassword != null) && (authDigestAlgorithm != null) && (privLogin != null) && (privPassword != null) && (privEncryptionAlgorithm != null)) {
						clientConfigurator.withLoginPassword(authLogin, authPassword, authDigestAlgorithm, privLogin, privPassword, privEncryptionAlgorithm);
					}

					c = new Hold(new SnmpClient(clientConfigurator.withAddress(address)));
					
					final Hold cc = c;
					clients.put(address, cc);
					c.handlers.add(clientHandler);
					
					c.client.connect(new CacheFailSnmpClientHandler(new SnmpClientHandler() {
						@Override
						public void failed(IOException e) {
							clients.remove(address);
							for (SnmpClientHandler h : cc.handlers) {
								h.failed(e);
							}
							cc.client.close();
						}
						@Override
						public void close() {
							clients.remove(address);
							for (SnmpClientHandler h : cc.handlers) {
								h.close();
							}
							cc.client.close();
						}
						@Override
						public void launched(final Callback callback) {
							cc.launchedCallback = callback;
							for (SnmpClientHandler h : cc.handlers) {
								h.launched(new Callback() {
									@Override
									public void close() {
										// Connection never actually closed
										cc.handlers.remove(clientHandler);
									}
									@Override
									public void get(Oid oid, GetCallback getCallback) {
										callback.get(oid, getCallback);
									}
								});
							}
						}
					}));
				} else {
					final Hold cc = c;
					
					cc.handlers.add(clientHandler);
					if (cc.launchedCallback != null) {
						clientHandler.launched(new SnmpClientHandler.Callback() {
							@Override
							public void close() {
								// Connection never actually closed
								cc.handlers.remove(clientHandler);
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
		for(Hold c : clients.values()) {
			if (c.launchedCallback != null) {
				c.launchedCallback.close();
			}
			c.client.close();
		}
		clients.clear();
	}
}
