package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.NinioProvider;
import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.core.UdpSocket;
import com.davfx.ninio.snmp.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.MemoryCache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public final class SnmpClient implements SnmpConnecter {
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpClient.class);

	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(SnmpClient.class.getPackage().getName());

	public static final int DEFAULT_PORT = 161;
	public static final int DEFAULT_TRAP_PORT = 162;

	private static final int BULK_SIZE = CONFIG.getInt("bulkSize");
	private static final double AUTH_ENGINES_CACHE_DURATION = ConfigUtils.getDuration(CONFIG, "auth.cache");

	public static interface Builder extends NinioBuilder<SnmpConnecter> {
		@Deprecated
		Builder with(Executor executor);

		Builder with(NinioBuilder<Connecter> connecterFactory);
	}
	
	public static Builder builder() {
		return new Builder() {
			private NinioBuilder<Connecter> connecterFactory = UdpSocket.builder();
			
			@Deprecated
			@Override
			public Builder with(Executor executor) {
				return this;
			}
			
			@Override
			public Builder with(NinioBuilder<Connecter> connecterFactory) {
				this.connecterFactory = connecterFactory;
				return this;
			}

			@Override
			public SnmpConnecter create(NinioProvider ninioProvider) {
				return new SnmpClient(ninioProvider.executor(), connecterFactory.create(ninioProvider));
			}
		};
	}
	
	private final Executor executor;
	private final Connecter connecter;
	
	private final InstanceMapper instanceMapper;

	private final RequestIdProvider requestIdProvider = new RequestIdProvider();
	private final MemoryCache<Address, AuthRemoteEnginePendingRequestManager> authRemoteEngines = MemoryCache.<Address, AuthRemoteEnginePendingRequestManager> builder().expireAfterAccess(AUTH_ENGINES_CACHE_DURATION).build();
    private static LoadingCache<AuthRemoteSpecification, EncryptionEngine> authEngines = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build(new CacheLoader<AuthRemoteSpecification, EncryptionEngine>() {
                @Override
                public EncryptionEngine load(AuthRemoteSpecification key) throws Exception {
                    return new EncryptionEngine(key);
                }
            });


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
			private Address address;
			private Oid oid;
			private List<SnmpResult> trap = null;
			
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
			
			@Override
			public SnmpRequestBuilder build(Address address, Oid oid) {
				this.address = address;
				this.oid = oid;
				return this;
			}
			
			@Override
			public void cancel() {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						if (instance != null) {
							instance.cancel();
						}
					}
				});
			}
			
			@Override
			public SnmpRequestBuilder add(Oid oid, String value) {
				if (trap == null) {
					trap = new LinkedList<>();
				}
				trap.add(new SnmpResult(oid, value));
				return this;
			}

			@Override
			public Cancelable call(final SnmpCallType type, final SnmpReceiver r) {
				final AuthRemoteSpecification s = authRemoteSpecification;
				final Oid o = oid;
				final Address a = address;
				final String c = community;
				final Iterable<SnmpResult> t = (trap == null) ? null : ImmutableList.copyOf(trap);
				executor.execute(new Runnable() {
					@Override
					public void run() {
						if (instance != null) {
							throw new IllegalStateException();
						}
						
						instance = new Instance(connecter, instanceMapper, o, a, type, c, t);

						AuthRemoteEnginePendingRequestManager authRemoteEnginePendingRequestManager = null;
						if (s != null) {
							authRemoteEnginePendingRequestManager = authRemoteEngines.get(a);
							if (authRemoteEnginePendingRequestManager == null) {
								authRemoteEnginePendingRequestManager = new AuthRemoteEnginePendingRequestManager();
								authRemoteEngines.put(a, authRemoteEnginePendingRequestManager);
							}
							authRemoteEnginePendingRequestManager.update(authRemoteSpecification, a, connecter);
						}

						instance.receiver = r;
						instance.authRemoteEnginePendingRequestManager = authRemoteEnginePendingRequestManager;
						instance.launch();
					}
				});
				return this;
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
						AuthRemoteEnginePendingRequestManager authRemoteEnginePendingRequestManager = authRemoteEngines.get(address);
						boolean ready;
						if (authRemoteEnginePendingRequestManager != null) {
							ready = authRemoteEnginePendingRequestManager.isReady();
						} else {
							ready = true;
						}
						try {
							SnmpPacketParser parser = new SnmpPacketParser((authRemoteEnginePendingRequestManager == null) ? null : authRemoteEnginePendingRequestManager.engine, buffer);
							instanceId = parser.getRequestId();
							errorStatus = parser.getErrorStatus();
							errorIndex = parser.getErrorIndex();
							results = parser.getResults();
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
			public void failed(final IOException ioe) {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						instanceMapper.fail(ioe);
					}
				});
				
				if (callback != null) {
					callback.failed(ioe);
				}
			}
			
			@Override
			public void connected(Address address) {
				if (callback != null) {
					callback.connected(address);
				}
			}
			
			@Override
			public void closed() {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						instanceMapper.fail(new IOException("Closed"));
					}
				});
				
				if (callback != null) {
					callback.closed();
				}
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
		private static final Oid DISCOVER_OID = new Oid(new long[] { 1L, 1L });
		
		public static final class PendingRequest {
			public final SnmpCallType request;
			public final int instanceId;
			public final Oid oid;
//			public final Iterable<SnmpResult> trap;
			public final SendCallback sendCallback;

			public PendingRequest(SnmpCallType request, int instanceId, Oid oid, /*Iterable<SnmpResult> trap, */SendCallback sendCallback) {
				this.request = request;
				this.instanceId = instanceId;
				this.oid = oid;
//				this.trap = trap;
				this.sendCallback = sendCallback;
			}
		}
		
		public AuthRemoteEngine engine = null;
		public final List<PendingRequest> pendingRequests = new LinkedList<>();
		
		public AuthRemoteEnginePendingRequestManager() {
		}
		
		public void update(AuthRemoteSpecification authRemoteSpecification, Address address, Connecter connector) {
			if (engine == null) {
				engine = new AuthRemoteEngine(authRemoteSpecification, authEngines.getUnchecked(authRemoteSpecification));
				discoverIfNecessary(address, connector);
			} else {
				if (!engine.authRemoteSpecification.equals(authRemoteSpecification)) {
					engine = new AuthRemoteEngine(authRemoteSpecification, authEngines.getUnchecked(authRemoteSpecification));
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
			engine = new AuthRemoteEngine(engine.authRemoteSpecification, authEngines.getUnchecked(engine.authRemoteSpecification));
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
					case GET: {
						Version3PacketBuilder builder = Version3PacketBuilder.get(engine, r.instanceId, r.oid);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GET v3: {} #{}, packet size = {}", r.oid, r.instanceId, b.remaining());
						connector.send(address, b, r.sendCallback);
						break;
					}
					case GETNEXT: {
						Version3PacketBuilder builder = Version3PacketBuilder.getNext(engine, r.instanceId, r.oid);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GETNEXT v3: {} #{}, packet size = {}", r.oid, r.instanceId, b.remaining());
						connector.send(address, b, r.sendCallback);
						break;
					}
					case GETBULK: {
						Version3PacketBuilder builder = Version3PacketBuilder.getBulk(engine, r.instanceId, r.oid, BULK_SIZE);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GETBULK v3: {} #{}, packet size = {}", r.oid, r.instanceId, b.remaining());
						connector.send(address, b, r.sendCallback);
						break;
					}
					case TRAP: {
						LOGGER.error("No TRAP possible in v3: {} #{}", r.oid, r.instanceId);
/*
						Version3PacketBuilder builder = Version3PacketBuilder.trap(engine, r.instanceId, r.oid, r.trap);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing TRAP v3: {} #{}, packet size = {}", r.oid, r.instanceId, b.remaining());
						connector.send(address, b, r.sendCallback);
*/
						break;
					}
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

		public void fail(IOException ioe) {
			for (Instance i : instances.values()) {
				i.fail(ioe);
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
		
		private final Oid requestOid;
		public int instanceId = RequestIdProvider.IGNORE_ID;

		private final Address address;
		private final String community;
		private final SnmpCallType snmpCallType;
		private AuthRemoteEnginePendingRequestManager authRemoteEnginePendingRequestManager = null;
		
		private final Iterable<SnmpResult> trap;

		public Instance(Connecter connector, InstanceMapper instanceMapper, Oid requestOid, Address address, SnmpCallType snmpCallType, String community, Iterable<SnmpResult> trap) {
			this.connector = connector;
			this.instanceMapper = instanceMapper;
			
			this.requestOid = requestOid;
			
			this.address = address;
			this.snmpCallType = snmpCallType;
			this.community = community;
			
			this.trap = trap;
		}
		
		public void launch() {
			if (receiver != null) {
				instanceMapper.map(this);
			}
			write();
		}
		
		public void close() {
			receiver = null;
		}
		
		public void cancel() {
			instanceMapper.unmap(this);
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
				switch (snmpCallType) { 
					case GET: {
						Version2cPacketBuilder builder = Version2cPacketBuilder.get(community, instanceId, requestOid);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GET: {} #{} ({}), packet size = {}", requestOid, instanceId, community, b.remaining());
						connector.send(address, b, sendCallback);
						break;
					}
					case GETNEXT: {
						Version2cPacketBuilder builder = Version2cPacketBuilder.getNext(community, instanceId, requestOid);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GETNEXT: {} #{} ({}), packet size = {}", requestOid, instanceId, community, b.remaining());
						connector.send(address, b, sendCallback);
						break;
					}
					case GETBULK: {
						Version2cPacketBuilder builder = Version2cPacketBuilder.getBulk(community, instanceId, requestOid, BULK_SIZE);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GETBULK: {} #{} ({}), packet size = {}", requestOid, instanceId, community, b.remaining());
						connector.send(address, b, sendCallback);
						break;
					}
					case TRAP: {
						Version2cPacketBuilder builder = Version2cPacketBuilder.trap(community, instanceId, requestOid, trap);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing TRAP: {} #{} ({}), packet size = {}", requestOid, instanceId, community, b.remaining());
						connector.send(address, b, sendCallback);
						break;
					}
					default:
						break;
				}
			} else {
				authRemoteEnginePendingRequestManager.registerPendingRequest(new AuthRemoteEnginePendingRequestManager.PendingRequest(snmpCallType, instanceId, requestOid, /*trap, */sendCallback));
				authRemoteEnginePendingRequestManager.sendPendingRequestsIfReady(address, connector);
			}
		}
	
		public void fail(IOException e) {
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

			for (SnmpResult r : results) {
				if (r.value == null) {
					continue;
				}
				LOGGER.trace("Addind to results: {}", r);
				if (receiver != null) {
					receiver.received(r);
				}
			}
			if (receiver != null) {
				receiver.finished();
			}
			receiver = null;
		}
	}
}
