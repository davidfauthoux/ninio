package com.davfx.ninio.snmp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.core.UdpSocket;
import com.davfx.ninio.util.ConfigUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.typesafe.config.Config;

public final class SnmpClient implements SnmpConnecter {
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpClient.class);

	private static final Config CONFIG = ConfigUtils.load(SnmpClient.class);

	public static final int DEFAULT_PORT = 161;

	private static final int BULK_SIZE = CONFIG.getInt("bulkSize");
	// private static final int GET_LIMIT = CONFIG.getInt("getLimit");
	private static final double AUTH_ENGINES_CACHE_DURATION = ConfigUtils.getDuration(CONFIG, "auth.cache");

	public static interface Builder extends NinioBuilder<SnmpConnecter> {
		Builder with(Executor executor);
		Builder with(NinioBuilder<Connecter> connecterFactory);
	}
	
	public static Builder builder() {
		return new Builder() {
			private Executor executor = null;
			private NinioBuilder<Connecter> connecterFactory = UdpSocket.builder();
			
			@Override
			public Builder with(Executor executor) {
				this.executor = executor;
				return this;
			}
			
			@Override
			public Builder with(NinioBuilder<Connecter> connecterFactory) {
				this.connecterFactory = connecterFactory;
				return this;
			}

			@Override
			public SnmpConnecter create(Queue queue) {
				if (executor == null) {
					throw new NullPointerException("executor");
				}
				return new SnmpClient(executor, connecterFactory.create(queue));
			}
		};
	}
	
	private final Executor executor;
	private final Connecter connecter;
	
	private final InstanceMapper instanceMapper;

	private final RequestIdProvider requestIdProvider = new RequestIdProvider();
	private final Cache<Address, AuthRemoteEnginePendingRequestManager> authRemoteEngines = CacheBuilder.newBuilder().expireAfterAccess((long) (AUTH_ENGINES_CACHE_DURATION * 1000d), TimeUnit.MILLISECONDS).build();

	private SnmpClient(Executor executor, Connecter connecter) {
		this.executor = executor;
		this.connecter = connecter;
		instanceMapper = new InstanceMapper(requestIdProvider);
	}
	
	@Override
	public SnmpRequestBuilder request() {
		return new SnmpRequestBuilder() {
			private String community = null;
			private AuthRemoteSpecification authRemoteSpecification = null;
			
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
			
			private Instance instance = null;
			private boolean receiverSet = false;
			private Cancelable cancelable = null;
			private AuthRemoteEnginePendingRequestManager authRemoteEnginePendingRequestManager;
			
			@Override
			public SnmpRequestBuilderCancelable build(final Address address, Oid oid) {
				if (cancelable != null) {
					throw new IllegalStateException();
				}
				
				instance = new Instance(connecter, instanceMapper, oid, address, community);

				cancelable = new Cancelable() {
					@Override
					public void cancel() {
						executor.execute(new Runnable() {
							@Override
							public void run() {
								instance.cancel();
							}
						});
					}
				};
				
				final AuthRemoteSpecification s = authRemoteSpecification;
				executor.execute(new Runnable() {
					@Override
					public void run() {
						if (s != null) {
							authRemoteEnginePendingRequestManager = authRemoteEngines.getIfPresent(address);
							if (authRemoteEnginePendingRequestManager == null) {
								authRemoteEnginePendingRequestManager = new AuthRemoteEnginePendingRequestManager();
								authRemoteEngines.put(address, authRemoteEnginePendingRequestManager);
							}
							authRemoteEnginePendingRequestManager.update(authRemoteSpecification, address, connecter);
						}
					}
				});


				return new SnmpRequestBuilderCancelableImpl(this, cancelable);
			}

			@Override
			public Cancelable receive(final SnmpReceiver c) {
				if (cancelable == null) {
					throw new IllegalStateException();
				}
				if (receiverSet) {
					throw new IllegalStateException();
				}
				
				receiverSet = true;
				
				executor.execute(new Runnable() {
					@Override
					public void run() {
						instance.receiver = c;
						instance.authRemoteEnginePendingRequestManager = authRemoteEnginePendingRequestManager;
						instance.launch();
					}
				});

				return cancelable;
			}
		};
	}
	@Override
	public void connect(final SnmpConnection callback) {
		connecter.connect(new Connection() {
			@Override
			public void received(final Address address, final ByteBuffer buffer) {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						LOGGER.trace("Received SNMP packet, size = {}", buffer.remaining());
						int instanceId;
						int errorStatus;
						int errorIndex;
						Iterable<SnmpResult> results;
						AuthRemoteEnginePendingRequestManager authRemoteEnginePendingRequestManager = authRemoteEngines.getIfPresent(address);
						boolean ready;
						if (authRemoteEnginePendingRequestManager != null) {
							ready = authRemoteEnginePendingRequestManager.isReady();
						} else {
							ready = true;
						}
						try {
							if (authRemoteEnginePendingRequestManager == null) {
								Version2cPacketParser parser = new Version2cPacketParser(buffer);
								instanceId = parser.getRequestId();
								errorStatus = parser.getErrorStatus();
								errorIndex = parser.getErrorIndex();
								results = parser.getResults();
							} else {
								Version3PacketParser parser = new Version3PacketParser(authRemoteEnginePendingRequestManager.engine, buffer);
								instanceId = parser.getRequestId();
								errorStatus = parser.getErrorStatus();
								errorIndex = parser.getErrorIndex();
								results = parser.getResults();
							}
						} catch (Exception e) {
							LOGGER.error("Invalid packet", e);
							return;
						}
						
						if (authRemoteEnginePendingRequestManager != null) {
							if (ready && (errorStatus == BerConstants.ERROR_STATUS_AUTHENTICATION_NOT_SYNCED)) {
								authRemoteEnginePendingRequestManager.reset();
							}

							authRemoteEnginePendingRequestManager.discoverIfNecessary(address, connecter);
							authRemoteEnginePendingRequestManager.sendPendingRequestsIfReady(address, connecter);
						}
						
						instanceMapper.handle(instanceId, errorStatus, errorIndex, results);
					}
				});
			}
			
			@Override
			public void failed(IOException ioe) {
				callback.failed(ioe);
			}
			
			@Override
			public void connected(Address address) {
				callback.connected(address);
			}
			
			@Override
			public void closed() {
				callback.closed();
			}
		});
	}
	
	@Override
	public void close() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				instanceMapper.close();
			}
		});
		
		connecter.close();
	}
	
	private static final class AuthRemoteEnginePendingRequestManager {
		private static final Oid DISCOVER_OID = new Oid(new int[] { 1, 1 });
		
		public static final class PendingRequest {
			public final int request;
			public final int instanceId;
			public final Oid oid;
			public final SendCallback sendCallback;

			public PendingRequest(int request, int instanceId, Oid oid, SendCallback sendCallback) {
				this.request = request;
				this.instanceId = instanceId;
				this.oid = oid;
				this.sendCallback = sendCallback;
			}
		}
		
		public AuthRemoteEngine engine = null;
		public final List<PendingRequest> pendingRequests = new LinkedList<>();
		
		public AuthRemoteEnginePendingRequestManager() {
		}
		
		public void update(AuthRemoteSpecification authRemoteSpecification, Address address, Connecter connector) {
			if (engine == null) {
				engine = new AuthRemoteEngine(authRemoteSpecification);
				discoverIfNecessary(address, connector);
			} else {
				if (!engine.authRemoteSpecification.equals(authRemoteSpecification)) {
					engine = new AuthRemoteEngine(authRemoteSpecification);
					discoverIfNecessary(address, connector);
				}
			}
		}
		
		public boolean isReady() {
			if ((engine.getId() == null) || (engine.getBootCount() == 0) || (engine.getTime() == 0)) {
				return false;
			}
			return true;
		}
		
		public void reset() {
			engine = new AuthRemoteEngine(engine.authRemoteSpecification);
		}
		
		public void discoverIfNecessary(Address address, Connecter connector) {
			if ((engine.getId() == null) || (engine.getBootCount() == 0) || (engine.getTime() == 0)) {
				Version3PacketBuilder builder = Version3PacketBuilder.get(engine, RequestIdProvider.IGNORE_ID, DISCOVER_OID);
				ByteBuffer b = builder.getBuffer();
				LOGGER.trace("Writing discover GET v3: {} #{}, packet size = {}", DISCOVER_OID, RequestIdProvider.IGNORE_ID, b.remaining());
				connector.send(address, b, new SendCallback() {
					@Override
					public void sent() {
					}
					@Override
					public void failed(IOException ioe) {
						IOException e = new IOException("Failed to send discover packet", ioe);
						for (PendingRequest r : pendingRequests) {
							r.sendCallback.failed(e);
						}
						pendingRequests.clear();
					}
				});
			}
		}
		
		public void registerPendingRequest(PendingRequest r) {
			pendingRequests.add(r);
		}
		
		public void sendPendingRequestsIfReady(Address address, Connecter connector) {
			if ((engine.getId() == null) || (engine.getBootCount() == 0) || (engine.getTime() == 0)) {
				return;
			}
			
			for (PendingRequest r : pendingRequests) {
				switch (r.request) {
				case BerConstants.GET: {
						Version3PacketBuilder builder = Version3PacketBuilder.get(engine, r.instanceId, r.oid);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GET v3: {} #{}, packet size = {}", r.oid, r.instanceId, b.remaining());
						connector.send(address, b, r.sendCallback);
					}
					break;
				case BerConstants.GETNEXT:
					{
						Version3PacketBuilder builder = Version3PacketBuilder.getNext(engine, r.instanceId, r.oid);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GETNEXT v3: {} #{}, packet size = {}", r.oid, r.instanceId, b.remaining());
						connector.send(address, b, r.sendCallback);
					}
					break;
				case BerConstants.GETBULK:
					{
						Version3PacketBuilder builder = Version3PacketBuilder.getBulk(engine, r.instanceId, r.oid, BULK_SIZE);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GETBULK v3: {} #{}, packet size = {}", r.oid, r.instanceId, b.remaining());
						connector.send(address, b, r.sendCallback);
					}
					break;
				default:
					break;
				}
			}
			pendingRequests.clear();
		}
	}
	
	private static final class RequestIdProvider {

		private static final Random RANDOM = new SecureRandom();

		public static final int IGNORE_ID = 0;
		
		private static final int MAX_ID = 2_000_000_000;
		private static final int INITIAL_VARIABILITY = 100_000;
		
		private static int NEXT = MAX_ID;
		
		private static final Object LOCK = new Object();

		public RequestIdProvider() {
		}
		
		public int get() {
			synchronized (LOCK) {
				if (NEXT == MAX_ID) {
					NEXT = IGNORE_ID + 1 + RANDOM.nextInt(INITIAL_VARIABILITY);
				}
				int k = NEXT;
				NEXT++;
				return k;
			}
		}
	}
	
	private static final class InstanceMapper {
		private final RequestIdProvider requestIdProvider;
		private final Map<Integer, Instance> instances = new HashMap<>();
		
		public InstanceMapper(RequestIdProvider requestIdProvider) {
			this.requestIdProvider = requestIdProvider;
		}
		
		public void map(Instance instance) {
			instances.remove(instance.instanceId);
			
			int instanceId = requestIdProvider.get();

			if (instances.containsKey(instanceId)) {
				LOGGER.warn("The maximum number of simultaneous request has been reached");
				return;
			}
			
			instances.put(instanceId, instance);
			
			LOGGER.trace("New instance ID = {}", instanceId);
			instance.instanceId = instanceId;
		}
		
		public void unmap(Instance instance) {
			instances.remove(instance.instanceId);
			instance.instanceId = RequestIdProvider.IGNORE_ID;
		}
		
		public void close() {
			for (Instance i : instances.values()) {
				i.close();
			}
			instances.clear();
		}

		public void handle(int instanceId, int errorStatus, int errorIndex, Iterable<SnmpResult> results) {
			if (instanceId == Integer.MAX_VALUE) {
				LOGGER.trace("Calling all instances (request ID = {})", Integer.MAX_VALUE);
				List<Instance> l = new LinkedList<>(instances.values());
				instances.clear();
				for (Instance i : l) {
					i.handle(errorStatus, errorIndex, results);
				}
				return;
			}
			
			Instance i = instances.remove(instanceId);
			if (i == null) {
				return;
			}
			i.handle(errorStatus, errorIndex, results);
		}
	}
	
	private static final class Instance {
		private final Connecter connector;
		private final InstanceMapper instanceMapper;
		
		private SnmpReceiver receiver;
		
		private final Oid initialRequestOid;
		private Oid requestOid;
		// private int countResults = 0;
		private int shouldRepeatWhat = BerConstants.GET;
		public int instanceId = RequestIdProvider.IGNORE_ID;

		private final Address address;
		private final String community;
		private AuthRemoteEnginePendingRequestManager authRemoteEnginePendingRequestManager = null;

		public Instance(Connecter connector, InstanceMapper instanceMapper, Oid requestOid, Address address, String community) {
			this.connector = connector;
			this.instanceMapper = instanceMapper;
			
			this.requestOid = requestOid;
			initialRequestOid = requestOid;
			
			this.address = address;
			this.community = community;
		}
		
		public void launch() {
			instanceMapper.map(this);
			shouldRepeatWhat = BerConstants.GET;
			write();
		}
		
		public void close() {
			shouldRepeatWhat = 0;
			requestOid = null;
			receiver = null;
		}
		
		public void cancel() {
			instanceMapper.unmap(this);
			shouldRepeatWhat = 0;
			requestOid = null;
			receiver = null;
		}
		
		private void write() {
			SendCallback sendCallback = new SendCallback() {
				@Override
				public void sent() {
				}
				@Override
				public void failed(IOException ioe) {
					fail(ioe);
				}
			};
			
			if (authRemoteEnginePendingRequestManager == null) {
				switch (shouldRepeatWhat) { 
				case BerConstants.GET: {
					Version2cPacketBuilder builder = Version2cPacketBuilder.get(community, instanceId, requestOid);
					ByteBuffer b = builder.getBuffer();
					LOGGER.trace("Writing GET: {} #{} ({}), packet size = {}", requestOid, instanceId, community, b.remaining());
					connector.send(address, b, sendCallback);
					break;
				}
				case BerConstants.GETNEXT: {
					Version2cPacketBuilder builder = Version2cPacketBuilder.getNext(community, instanceId, requestOid);
					ByteBuffer b = builder.getBuffer();
					LOGGER.trace("Writing GETNEXT: {} #{} ({}), packet size = {}", requestOid, instanceId, community, b.remaining());
					connector.send(address, b, sendCallback);
					break;
				}
				case BerConstants.GETBULK: {
					Version2cPacketBuilder builder = Version2cPacketBuilder.getBulk(community, instanceId, requestOid, BULK_SIZE);
					ByteBuffer b = builder.getBuffer();
					LOGGER.trace("Writing GETBULK: {} #{} ({}), packet size = {}", requestOid, instanceId, community, b.remaining());
					connector.send(address, b, sendCallback);
					break;
				}
				default:
					break;
				}
			} else {
				authRemoteEnginePendingRequestManager.registerPendingRequest(new AuthRemoteEnginePendingRequestManager.PendingRequest(shouldRepeatWhat, instanceId, requestOid, sendCallback));
				authRemoteEnginePendingRequestManager.sendPendingRequestsIfReady(address, connector);
			}
		}
	
		private void fail(IOException e) {
			shouldRepeatWhat = 0;
			requestOid = null;
			if (receiver != null) {
				receiver.failed(e);
			}
			receiver = null;
		}
		
		private void handle(int errorStatus, int errorIndex, Iterable<SnmpResult> results) {
			if (requestOid == null) {
				return;
			}

			if (errorStatus == BerConstants.ERROR_STATUS_AUTHENTICATION_NOT_SYNCED) {
				fail(new IOException("Authentication engine not synced"));
				return;
			}

			if (errorStatus == BerConstants.ERROR_STATUS_AUTHENTICATION_FAILED) {
				fail(new IOException("Authentication failed"));
				return;
			}
			
			if (errorStatus == BerConstants.ERROR_STATUS_TIMEOUT) {
				fail(new IOException("Timeout"));
				return;
			}

			if (errorStatus != 0) {
				LOGGER.trace("Received error: {}/{}", errorStatus, errorIndex);
			}

			if (shouldRepeatWhat == BerConstants.GET) {
				SnmpResult found = null;
				for (SnmpResult r : results) {
					if (requestOid.equals(r.oid)) {
						LOGGER.trace("Scalar found: {}", r);
						found = r;
					}
				}
				if (found != null) {
					requestOid = null;
					if (receiver != null) {
						receiver.received(found);
						receiver.finished();
					}
					receiver = null;
					return;
				} else {
					instanceMapper.map(this);
					shouldRepeatWhat = BerConstants.GETBULK;
					write();
				}
			} else {
				Oid lastOid = null;
				for (SnmpResult r : results) {
					LOGGER.trace("Received in bulk: {}", r);
				}
				for (SnmpResult r : results) {
					if (r.value == null) {
						continue;
					}
					if (!initialRequestOid.isPrefixOf(r.oid)) {
						LOGGER.trace("{} not prefixed by {}", r.oid, initialRequestOid);
						lastOid = null;
						break;
					}
					LOGGER.trace("Addind to results: {}", r);
					/*
					if ((GET_LIMIT > 0) && (countResults >= GET_LIMIT)) {
						LOGGER.warn("{} reached limit", requestOid);
						lastOid = null;
						break;
					}
					countResults++;
					*/
					if (receiver != null) {
						receiver.received(r);
					}
					lastOid = r.oid;
				}
				if (lastOid != null) {
					LOGGER.trace("Continuing from: {}", lastOid);
					
					requestOid = lastOid;
					
					instanceMapper.map(this);
					shouldRepeatWhat = BerConstants.GETBULK;
					write();
				} else {
					// Stop here
					requestOid = null;
					if (receiver != null) {
						receiver.finished();
					}
					receiver = null;
				}
			}
		}
	}
}
