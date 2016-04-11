package com.davfx.ninio.snmp.v3;

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
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.ConnectorFactory;
import com.davfx.ninio.core.v3.DatagramConnectorFactory;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.Receiver;
import com.davfx.ninio.core.v3.Shared;
import com.davfx.ninio.snmp.AuthRemoteEngine;
import com.davfx.ninio.snmp.AuthRemoteSpecification;
import com.davfx.ninio.snmp.BerConstants;
import com.davfx.ninio.snmp.Oid;
import com.davfx.ninio.snmp.Result;
import com.davfx.ninio.snmp.Version2cPacketBuilder;
import com.davfx.ninio.snmp.Version2cPacketParser;
import com.davfx.ninio.snmp.Version3PacketBuilder;
import com.davfx.ninio.snmp.Version3PacketParser;
import com.davfx.util.ConfigUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class SnmpClient implements AutoCloseable, Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpClient.class);

	private static final Config CONFIG = ConfigFactory.load(SnmpClient.class.getClassLoader());

	public static final int DEFAULT_PORT = 161;

	private static final int BULK_SIZE = CONFIG.getInt("ninio.snmp.bulkSize");
	private static final int GET_LIMIT = CONFIG.getInt("ninio.snmp.getLimit");
	private static final double AUTH_ENGINES_CACHE_DURATION = ConfigUtils.getDuration(CONFIG, "ninio.snmp.auth.cache");
	
	private Executor executor = Shared.EXECUTOR;
	private ConnectorFactory connectorFactory = new DatagramConnectorFactory();
	
	private Connector connector = null;
	private InstanceMapper instanceMapper = null;

	private final RequestIdProvider requestIdProvider = new RequestIdProvider();
	private final Cache<Address, AuthRemoteEnginePendingRequestManager> authRemoteEngines = CacheBuilder.newBuilder().expireAfterAccess((long) (AUTH_ENGINES_CACHE_DURATION * 1000d), TimeUnit.MILLISECONDS).build();

	public SnmpClient() {
	}

	public SnmpClient with(Executor executor) {
		this.executor = executor;
		return this;
	}
	
	public SnmpClient with(ConnectorFactory connectorFactory) {
		this.connectorFactory = connectorFactory;
		return this;
	}

	public SnmpClient connect() {
		Connector thisConnector = connector;
		connector = null;
		if (thisConnector != null) {
			thisConnector.disconnect();
		}

		connector = connectorFactory.create();
		thisConnector = connector;

		final Executor thisExecutor = executor;
		
		instanceMapper = new InstanceMapper(requestIdProvider);
		final InstanceMapper thisInstanceMapper = instanceMapper;
		final Connector thisThisConnector = thisConnector;

		thisConnector.receiving(new Receiver() {
			@Override
			public void received(final Address address, final ByteBuffer buffer) {
				thisExecutor.execute(new Runnable() {
					@Override
					public void run() {
						LOGGER.trace("Received SNMP packet, size = {}", buffer.remaining());
						int instanceId;
						int errorStatus;
						int errorIndex;
						Iterable<Result> results;
						AuthRemoteEnginePendingRequestManager authRemoteEnginePendingRequestManager = authRemoteEngines.getIfPresent(address);
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
							authRemoteEnginePendingRequestManager.discoverIfNecessary(address, thisThisConnector);
							authRemoteEnginePendingRequestManager.sendPendingRequestsIfReady(address, thisThisConnector);
						}
						
						thisInstanceMapper.handle(instanceId, errorStatus, errorIndex, results);
					}
				});
			}
		});
		
		thisConnector.connect();
		
		return this;
	}
	
	@Override
	public void close() {
		final Executor thisExecutor = executor;

		final InstanceMapper thisInstanceMapper = instanceMapper;
		instanceMapper = null;
		if (thisInstanceMapper != null) {
			thisExecutor.execute(new Runnable() {
				@Override
				public void run() {
					thisInstanceMapper.close();
				}
			});
		}
		
		Connector thisConnector = connector;
		connector = null;
		if (thisConnector != null) {
			thisConnector.disconnect();
		}
	}
	
	private static final class AuthRemoteEnginePendingRequestManager {
		private static final Oid DISCOVER_OID = new Oid(new int[] { 1, 1 });
		
		public static final class PendingRequest {
			public final int request;
			public final int instanceId;
			public final Oid oid;

			public PendingRequest(int request, int instanceId, Oid oid) {
				this.request = request;
				this.instanceId = instanceId;
				this.oid = oid;
			}
		}
		
		public AuthRemoteEngine engine = null;
		public final List<PendingRequest> pendingRequests = new LinkedList<>();
		
		public AuthRemoteEnginePendingRequestManager() {
		}
		
		public void update(AuthRemoteSpecification authRemoteSpecification) {
			if (engine == null) {
				engine = new AuthRemoteEngine(authRemoteSpecification);
			} else {
				if (!engine.authRemoteSpecification.equals(authRemoteSpecification)) {
					engine = new AuthRemoteEngine(authRemoteSpecification);
				}
			}
		}
		
		public void discoverIfNecessary(Address address, Connector connector) {
			if ((engine.getId() == null) || (engine.getBootCount() == 0) || (engine.getTime() == 0)) {
				Version3PacketBuilder builder = Version3PacketBuilder.get(engine, Integer.MAX_VALUE, DISCOVER_OID);
				ByteBuffer b = builder.getBuffer();
				LOGGER.trace("Writing discover GET v3: {} #{}, packet size = {}", DISCOVER_OID, Integer.MAX_VALUE, b.remaining());
				connector.send(address, b);
			}
		}
		
		public void registerPendingRequest(PendingRequest r) {
			pendingRequests.add(r);
		}
		
		public void sendPendingRequestsIfReady(Address address, Connector connector) {
			if ((engine.getId() == null) || (engine.getBootCount() == 0) || (engine.getTime() == 0)) {
				return;
			}
			
			for (PendingRequest r : pendingRequests) {
				switch (r.request) { 
				case BerConstants.GET: {
						Version3PacketBuilder builder = Version3PacketBuilder.get(engine, r.instanceId, r.oid);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GET v3: {} #{}, packet size = {}", r.oid, r.instanceId, b.remaining());
						connector.send(address, b);
					}
					break;
				case BerConstants.GETNEXT:
					{
						Version3PacketBuilder builder = Version3PacketBuilder.getNext(engine, r.instanceId, r.oid);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GETNEXT v3: {} #{}, packet size = {}", r.oid, r.instanceId, b.remaining());
						connector.send(address, b);
					}
					break;
				case BerConstants.GETBULK:
					{
						Version3PacketBuilder builder = Version3PacketBuilder.getBulk(engine, r.instanceId, r.oid, BULK_SIZE);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GETBULK v3: {} #{}, packet size = {}", r.oid, r.instanceId, b.remaining());
						connector.send(address, b);
					}
					break;
				default:
					break;
				}
			}
			pendingRequests.clear();
		}
	}
	
	public SnmpRequest request() {
		final Executor thisExecutor = executor;
		final InstanceMapper thisInstanceMapper = instanceMapper;
		final Connector thisConnector = connector;

		return new SnmpRequest() {
			private SnmpReceiver receiver = null;
			private Failing failing = null;
			
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
			public SnmpRequest get(final Address address, final String community, final AuthRemoteSpecification authRemoteSpecification, final Oid oid) {
				final SnmpReceiver r = receiver;
				final Failing f = failing;
				thisExecutor.execute(new Runnable() {
					@Override
					public void run() {
						AuthRemoteEnginePendingRequestManager authRemoteEnginePendingRequestManager = null;
						if (authRemoteSpecification != null) {
							authRemoteEnginePendingRequestManager = authRemoteEngines.getIfPresent(address);
							if (authRemoteEnginePendingRequestManager == null) {
								authRemoteEnginePendingRequestManager = new AuthRemoteEnginePendingRequestManager();
								authRemoteEngines.put(address, authRemoteEnginePendingRequestManager);
								authRemoteEnginePendingRequestManager.update(authRemoteSpecification);
								authRemoteEnginePendingRequestManager.discoverIfNecessary(address, thisConnector);
							} else {
								authRemoteEnginePendingRequestManager.update(authRemoteSpecification);
							}
						}
						
						new Instance(thisConnector, thisInstanceMapper, r, f, oid, address, community, authRemoteEnginePendingRequestManager);
					}
				});
				return this;
			}
		};
	}
	
	private static final class RequestIdProvider {

		private static final Random RANDOM = new SecureRandom();
		private static final int INITIAL_VARIABILITY = 100000;
		private static int NEXT = Integer.MAX_VALUE;
		private static final Object LOCK = new Object();

		public RequestIdProvider() {
		}
		
		public int get() {
			synchronized (LOCK) {
				if (NEXT == Integer.MAX_VALUE) {
					NEXT = RANDOM.nextInt(INITIAL_VARIABILITY);
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
			int instanceId = requestIdProvider.get();

			if (instances.containsKey(instanceId)) {
				LOGGER.warn("The maximum number of simultaneous request has been reached");
				return;
			}
			
			instances.put(instanceId, instance);
			
			LOGGER.trace("New instance ID = {}", instanceId);
			instance.instanceId = instanceId;
		}
		
		public void close() {
			for (Instance i : instances.values()) {
				i.close();
			}
			instances.clear();
		}

		public void handle(int instanceId, int errorStatus, int errorIndex, Iterable<Result> results) {
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
		private final Connector connector;
		private final InstanceMapper instanceMapper;
		
		private SnmpReceiver receiver;
		private Failing failing;
		
		private final Oid initialRequestOid;
		private Oid requestOid;
		private int countResults = 0;
		private int shouldRepeatWhat = BerConstants.GET;
		public int instanceId;

		private final Address address;
		private final String community;
		private final AuthRemoteEnginePendingRequestManager authRemoteEnginePendingRequestManager;

		public Instance(Connector connector, InstanceMapper instanceMapper, SnmpReceiver receiver, Failing failing, Oid requestOid, Address address, String community, AuthRemoteEnginePendingRequestManager authRemoteEnginePendingRequestManager) {
			this.connector = connector;
			this.instanceMapper = instanceMapper;
			
			this.receiver = receiver;
			this.failing = failing;

			this.requestOid = requestOid;
			initialRequestOid = requestOid;
			
			this.address = address;
			this.community = community;
			this.authRemoteEnginePendingRequestManager = authRemoteEnginePendingRequestManager;

			instanceMapper.map(this);
			shouldRepeatWhat = BerConstants.GET;
			write();
		}
		
		public void close() {
			shouldRepeatWhat = 0;
			requestOid = null;
			receiver = null;
			failing = null;
		}
		
		private void write() {
			if (authRemoteEnginePendingRequestManager == null) {
				switch (shouldRepeatWhat) { 
				case BerConstants.GET: {
					Version2cPacketBuilder builder = Version2cPacketBuilder.get(community, instanceId, requestOid);
					ByteBuffer b = builder.getBuffer();
					LOGGER.trace("Writing GET: {} #{} ({}), packet size = {}", requestOid, instanceId, community, b.remaining());
					connector.send(address, b);
					break;
				}
				case BerConstants.GETNEXT: {
					Version2cPacketBuilder builder = Version2cPacketBuilder.getNext(community, instanceId, requestOid);
					ByteBuffer b = builder.getBuffer();
					LOGGER.trace("Writing GETNEXT: {} #{} ({}), packet size = {}", requestOid, instanceId, community, b.remaining());
					connector.send(address, b);
					break;
				}
				case BerConstants.GETBULK: {
					Version2cPacketBuilder builder = Version2cPacketBuilder.getBulk(community, instanceId, requestOid, BULK_SIZE);
					ByteBuffer b = builder.getBuffer();
					LOGGER.trace("Writing GETBULK: {} #{} ({}), packet size = {}", requestOid, instanceId, community, b.remaining());
					connector.send(address, b);
					break;
				}
				default:
					break;
				}
			} else {
				authRemoteEnginePendingRequestManager.registerPendingRequest(new AuthRemoteEnginePendingRequestManager.PendingRequest(shouldRepeatWhat, instanceId, requestOid));
				authRemoteEnginePendingRequestManager.sendPendingRequestsIfReady(address, connector);
			}
		}
	
		private void fail(IOException e) {
			shouldRepeatWhat = 0;
			requestOid = null;
			failing.failed(new IOException("Timeout"));
			receiver = null;
			failing = null;
		}
		
		private void handle(int errorStatus, int errorIndex, Iterable<Result> results) {
			if (requestOid == null) {
				return;
			}

			if (errorStatus == BerConstants.ERROR_STATUS_AUTHENTICATION_NOT_SYNCED) {
				// Ignored
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
			
			if (shouldRepeatWhat == BerConstants.GET) {
				boolean fallback = false;
				if (errorStatus != 0) {
					LOGGER.trace("Fallbacking to GETNEXT/GETBULK after receiving error: {}/{}", errorStatus, errorIndex);
					fallback = true;
				} else {
					Result found = null;
					for (Result r : results) {
						if (r.getValue() == null) {
							LOGGER.trace(r.getOid() + " fallback to GETNEXT/GETBULK");
							fallback = true;
							break;
						} else if (!requestOid.equals(r.getOid())) {
							LOGGER.trace("{} not as expected: {}, fallbacking to GETNEXT/GETBULK", r.getOid(), requestOid);
							fallback = true;
							break;
						}
						
						// Cannot return more than one
						LOGGER.trace("Scalar found: {}", r);
						found = r;
					}
					if (found != null) {
						requestOid = null;
						receiver.received(found);
						receiver.finished();
						receiver = null;
						failing = null;
						return;
					}
				}
				if (fallback) {
					instanceMapper.map(this);
					shouldRepeatWhat = BerConstants.GETBULK;
					write();
				}
			} else {
				if (errorStatus != 0) {
					requestOid = null;
					receiver.finished();
					receiver = null;
					failing = null;
				} else {
					Oid lastOid = null;
					for (Result r : results) {
						LOGGER.trace("Received in bulk: {}", r);
					}
					for (Result r : results) {
						if (r.getValue() == null) {
							continue;
						}
						if (!initialRequestOid.isPrefixOf(r.getOid())) {
							LOGGER.trace("{} not prefixed by {}", r.getOid(), initialRequestOid);
							lastOid = null;
							break;
						}
						LOGGER.trace("Addind to results: {}", r);
						if ((GET_LIMIT > 0) && (countResults >= GET_LIMIT)) {
							LOGGER.warn("{} reached limit", requestOid);
							lastOid = null;
							break;
						}
						countResults++;
						receiver.received(r);
						lastOid = r.getOid();
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
						receiver.finished();
						receiver = null;
						failing = null;
					}
				}
			}
		}
	}
}
