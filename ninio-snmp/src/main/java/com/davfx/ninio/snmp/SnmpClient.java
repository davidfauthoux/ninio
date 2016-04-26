package com.davfx.ninio.snmp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferHandler;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.util.QueueScheduled;
import com.davfx.util.ConfigUtils;
import com.davfx.util.DateUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class SnmpClient implements AutoCloseable, Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpClient.class);

	private static final Config CONFIG = ConfigFactory.load(SnmpClient.class.getClassLoader());

	private static final int BULK_SIZE = CONFIG.getInt("ninio.snmp.bulkSize");
	private static final double MIN_TIME_TO_REPEAT = ConfigUtils.getDuration(CONFIG, "ninio.snmp.repeat.min");
	private static final int GET_LIMIT = CONFIG.getInt("ninio.snmp.getLimit");
	private static final double REPEAT_TIME = ConfigUtils.getDuration(CONFIG, "ninio.snmp.repeat.time");
	private static final double REPEAT_RANDOMIZATION = ConfigUtils.getDuration(CONFIG, "ninio.snmp.repeat.randomization");
	private static final double AUTH_ENGINES_CACHE_DURATION = ConfigUtils.getDuration(CONFIG, "ninio.snmp.auth.cache");
	
	private final Queue queue;
	private final ReadyFactory readyFactory;
	// private final Address address;
	// private final String community;
	// private final AuthRemoteEngine authEngine;
	// private final double timeoutFromBeginning;

	private final Closeable closeable;
	private final RequestIdProvider requestIdProvider = new RequestIdProvider();
	private final Set<InstanceMapper> instanceMappers = new HashSet<>();

	public SnmpClient(Queue queue, ReadyFactory readyFactory) {
	// public SnmpClient(Queue queue, ReadyFactory readyFactory, Address address, String community, AuthRemoteEngine authEngine, double timeoutFromBeginning) {
		this.queue = queue;
		this.readyFactory = readyFactory;
		// this.address = address;
		// this.community = community;
		// this.authEngine = authEngine;
		// this.timeoutFromBeginning = timeoutFromBeginning;
		
		closeable = QueueScheduled.schedule(queue, REPEAT_TIME, new Runnable() {
			@Override
			public void run() {
				double now = DateUtils.now();
				for (InstanceMapper i : instanceMappers) {
					i.repeat(now);
				}
				{
					Iterator<InstanceMapper> it = instanceMappers.iterator();
					while (it.hasNext()) {
						if (it.next().terminated) {
							it.remove();
						}
					}
				}
			}
		});
	}
	// public SnmpClient(Queue queue, ReadyFactory readyFactory, Address address, String community, double timeoutFromBeginning) {
	// this(queue, readyFactory, address, community, null, timeoutFromBeginning);
	// }
	// public SnmpClient(Queue queue, ReadyFactory readyFactory, Address address, AuthRemoteEngine authEngine, double timeoutFromBeginning) {
	// this(queue, readyFactory, address, null, authEngine, timeoutFromBeginning);
	// }
	
	@Override
	public void close() {
		closeable.close();
		queue.post(new Runnable() {
			@Override
			public void run() {
				for (InstanceMapper i : instanceMappers) {
					i.closeAll();
				}
				instanceMappers.clear();
			}
		});
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
		
		public void update(AuthRemoteSpecification authRemoteSpecification, Address address, ByteBufferHandler write) {
			if (engine == null) {
				engine = new AuthRemoteEngine(authRemoteSpecification);
				discoverIfNecessary(address, write);
			} else {
				if (!engine.authRemoteSpecification.equals(authRemoteSpecification)) {
					engine = new AuthRemoteEngine(authRemoteSpecification);
					discoverIfNecessary(address, write);
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
		
		public void discoverIfNecessary(Address address, ByteBufferHandler write) {
			if ((engine.getId() == null) || (engine.getBootCount() == 0) || (engine.getTime() == 0)) {
				Version3PacketBuilder builder = Version3PacketBuilder.get(engine, RequestIdProvider.IGNORE_ID, DISCOVER_OID);
				ByteBuffer b = builder.getBuffer();
				LOGGER.trace("Writing discover GET v3: {} #{}, packet size = {}", DISCOVER_OID, RequestIdProvider.IGNORE_ID, b.remaining());
				write.handle(address, b);
			}
		}
		
		public void registerPendingRequest(PendingRequest r) {
			pendingRequests.add(r);
		}
		
		public void sendPendingRequestsIfReady(Address address, ByteBufferHandler write) {
			if ((engine.getId() == null) || (engine.getBootCount() == 0) || (engine.getTime() == 0)) {
				return;
			}
			
			for (PendingRequest r : pendingRequests) {
				switch (r.request) { 
				case BerConstants.GET: {
						Version3PacketBuilder builder = Version3PacketBuilder.get(engine, r.instanceId, r.oid);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GET v3: {} #{}, packet size = {}", r.oid, r.instanceId, b.remaining());
						write.handle(address, b);
					}
					break;
				case BerConstants.GETNEXT:
					{
						Version3PacketBuilder builder = Version3PacketBuilder.getNext(engine, r.instanceId, r.oid);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GETNEXT v3: {} #{}, packet size = {}", r.oid, r.instanceId, b.remaining());
						write.handle(address, b);
					}
					break;
				case BerConstants.GETBULK:
					{
						Version3PacketBuilder builder = Version3PacketBuilder.getBulk(engine, r.instanceId, r.oid, BULK_SIZE);
						ByteBuffer b = builder.getBuffer();
						LOGGER.trace("Writing GETBULK v3: {} #{}, packet size = {}", r.oid, r.instanceId, b.remaining());
						write.handle(address, b);
					}
					break;
				default:
					{
						LOGGER.error("Invalid writing v3: {} #{}, request type = {}", r.oid, r.instanceId, r.request);
					}
					break;
				}
			}
			pendingRequests.clear();
		}
	}
	
	public void connect(final SnmpClientHandler clientHandler) {
		queue.post(new Runnable() {
			@Override
			public void run() {
				Ready ready = readyFactory.create();
				
				final InstanceMapper instanceMapper = new InstanceMapper(requestIdProvider);
				// final InstanceMapper instanceMapper = new InstanceMapper(address, requestIdProvider);
				instanceMappers.add(instanceMapper);
				
				ready.connect(null, new ReadyConnection() {
				// ready.connect(address, new ReadyConnection() {
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						if (instanceMapper.terminated) {
							return;
						}

						LOGGER.trace("Received SNMP packet, size = {}", buffer.remaining());
						int instanceId;
						int errorStatus;
						int errorIndex;
						Iterable<Result> results;
						AuthRemoteEnginePendingRequestManager authRemoteEnginePendingRequestManager = instanceMapper.authRemoteEngines.getIfPresent(address);
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

						LOGGER.trace("SNMP packet instanceId = {}", instanceId);

						if (authRemoteEnginePendingRequestManager != null) {
							if (ready && (errorStatus == BerConstants.ERROR_STATUS_AUTHENTICATION_NOT_SYNCED)) {
								authRemoteEnginePendingRequestManager.reset();
							}

							authRemoteEnginePendingRequestManager.discoverIfNecessary(address, instanceMapper.write);
							authRemoteEnginePendingRequestManager.sendPendingRequestsIfReady(address, instanceMapper.write);
						}
						
						instanceMapper.handle(instanceId, errorStatus, errorIndex, results);
					}
					
					@Override
					public void failed(IOException e) {
						if (instanceMapper.terminated) {
							return;
						}
						instanceMapper.terminated = true;
						clientHandler.failed(e);
					}
					
					@Override
					public void connected(final FailableCloseableByteBufferHandler write) {
						instanceMapper.write = write;
						
						clientHandler.launched(new SnmpClientHandler.Callback() {
							@Override
							public void close() {
								queue.post(new Runnable() {
									@Override
									public void run() {
										if (instanceMapper.terminated) {
											return;
										}
										instanceMapper.terminated = true;
										write.close();
									}
								});
							}
							@Override
							public void get(final Address address, final String community, final AuthRemoteSpecification authRemoteSpecification, final double timeout, final Oid oid, final GetCallback callback) {
							// public void get(Oid oid, GetCallback callback) {
								queue.post(new Runnable() {
									@Override
									public void run() {
										AuthRemoteEnginePendingRequestManager authRemoteEnginePendingRequestManager = null;
										if (authRemoteSpecification != null) {
											authRemoteEnginePendingRequestManager = instanceMapper.authRemoteEngines.getIfPresent(address);
											if (authRemoteEnginePendingRequestManager == null) {
												authRemoteEnginePendingRequestManager = new AuthRemoteEnginePendingRequestManager();
												instanceMapper.authRemoteEngines.put(address, authRemoteEnginePendingRequestManager);
											}
											authRemoteEnginePendingRequestManager.update(authRemoteSpecification, address, write);
										}
										
										new Instance(instanceMapper, callback, write, oid, timeout, address, community, authRemoteEnginePendingRequestManager);
									}
								});
							}
						});
					}
					
					@Override
					public void close() {
						if (instanceMapper.terminated) {
							return;
						}
						instanceMapper.terminated = true;
						clientHandler.close();
					}
				});
			}
		});
	}
	
	private static final class InstanceMapper { // extends CheckAllocationObject {
		// private final Address address;
		private final Cache<Address, AuthRemoteEnginePendingRequestManager> authRemoteEngines = CacheBuilder.newBuilder().expireAfterAccess((long) (AUTH_ENGINES_CACHE_DURATION * 1000d), TimeUnit.MILLISECONDS).build();

		private final Map<Integer, Instance> instances = new HashMap<>();
		private RequestIdProvider requestIdProvider;
		
		private FailableCloseableByteBufferHandler write = null;
		
		public boolean terminated = false;
		
		public InstanceMapper(RequestIdProvider requestIdProvider) {
		// public InstanceMapper(Address address, RequestIdProvider requestIdProvider) {
			// super(InstanceMapper.class);
			// this.address = address;
			this.requestIdProvider = requestIdProvider;
		}
		
		public void map(Instance instance) {
			if (terminated) {
				return;
			}
			
			instances.remove(instance.instanceId);
			
			int instanceId = requestIdProvider.get();

			if (instances.containsKey(instanceId)) {
				LOGGER.warn("The maximum number of simultaneous request has been reached"); // [{}]", address);
				return;
			}
			
			instances.put(instanceId, instance);
			
			LOGGER.trace("New instance ID = {}", instanceId);
			instance.instanceId = instanceId;
		}
		
		public void closeAll() {
			if (terminated) {
				return;
			}
			for (Instance i : instances.values()) {
				i.closeAll();
			}
			if (write != null) {
				write.close();
			}
			instances.clear();
		}

		public void handle(int instanceId, int errorStatus, int errorIndex, Iterable<Result> results) {
			if (terminated) {
				return;
			}
			/*%%%%%%
			if (instanceId == Integer.MAX_VALUE) {
				LOGGER.trace("Calling all instances (request ID = {})", Integer.MAX_VALUE);
				List<Instance> l = new LinkedList<>(instances.values());
				instances.clear();
				for (Instance i : l) {
					i.handle(errorStatus, errorIndex, results);
				}
				return;
			}
			*/
			
			Instance i = instances.remove(instanceId);
			if (i == null) {
				return;
			}
			i.handle(errorStatus, errorIndex, results);
		}
		
		public void repeat(double now) {
			if (terminated) {
				return;
			}
			
			for (Map.Entry<Address, AuthRemoteEnginePendingRequestManager> e : authRemoteEngines.asMap().entrySet()) {
				e.getValue().discoverIfNecessary(e.getKey(), write);
			}
			
			for (Instance i : instances.values()) {
				i.repeat(now);
			}
			
			Iterator<Instance> ii = instances.values().iterator();
			while (ii.hasNext()) {
				Instance i = ii.next();
				if (i.callback == null) {
					ii.remove();
				}
			}
		}
	}
	
	private static final class Instance {
		private static final Random RANDOM = new Random(System.currentTimeMillis());

		private final InstanceMapper instanceMapper;
		private SnmpClientHandler.Callback.GetCallback callback;
		private final CloseableByteBufferHandler write;
		private final Oid initialRequestOid;
		private Oid requestOid;
		private int countResults = 0;
		private final double timeout;
		private double sendTimestamp;
		private int shouldRepeatWhat;
		public int instanceId = RequestIdProvider.IGNORE_ID;
		private final double repeatRandomizationRandomized;

		private final Address address;
		private final String community;
		private final AuthRemoteEnginePendingRequestManager authRemoteEnginePendingRequestManager;

		public Instance(InstanceMapper instanceMapper, SnmpClientHandler.Callback.GetCallback callback, CloseableByteBufferHandler write, Oid requestOid, double timeout, Address address, String community, AuthRemoteEnginePendingRequestManager authRemoteEnginePendingRequestManager) {
			// super(Instance.class);
			this.instanceMapper = instanceMapper;
			this.callback = callback;
			this.write = write;
			this.requestOid = requestOid;
			this.timeout = timeout;
			initialRequestOid = requestOid;
			
			this.address = address;
			this.community = community;
			this.authRemoteEnginePendingRequestManager = authRemoteEnginePendingRequestManager;
			
			repeatRandomizationRandomized = (RANDOM.nextDouble() * REPEAT_RANDOMIZATION) - (1d / 2d); // [ -0.5, 0.5 [

			instanceMapper.map(this);
			sendTimestamp = DateUtils.now();
			shouldRepeatWhat = BerConstants.GET;
			write();
		}
		
		public void closeAll() {
			write.close();

			if (callback == null) {
				return;
			}
			if (requestOid == null) {
				return;
			}
			
			shouldRepeatWhat = 0;
			requestOid = null;
			callback = null;
		}
		
		public void repeat(double now) {
			if (callback == null) {
				return;
			}
			if (requestOid == null) {
				return;
			}
			
			double t = now - sendTimestamp;
			if (t >= timeout) {
				fail(new IOException("Timeout [" + t + " seconds] requesting: " + address + " " + initialRequestOid));
				return;
			}

			if (t >= (MIN_TIME_TO_REPEAT + repeatRandomizationRandomized)) {
				LOGGER.trace("Repeating {} {}", address, requestOid);
				write();
			}
		}
		
		private void write() {
			if (authRemoteEnginePendingRequestManager == null) {
				switch (shouldRepeatWhat) { 
				case BerConstants.GET: {
					Version2cPacketBuilder builder = Version2cPacketBuilder.get(community, instanceId, requestOid);
					ByteBuffer b = builder.getBuffer();
					LOGGER.trace("Writing GET: {} #{} ({}), packet size = {}", requestOid, instanceId, community, b.remaining());
					write.handle(address, b);
					break;
				}
				case BerConstants.GETNEXT: {
					Version2cPacketBuilder builder = Version2cPacketBuilder.getNext(community, instanceId, requestOid);
					ByteBuffer b = builder.getBuffer();
					LOGGER.trace("Writing GETNEXT: {} #{} ({}), packet size = {}", requestOid, instanceId, community, b.remaining());
					write.handle(address, b);
					break;
				}
				case BerConstants.GETBULK: {
					Version2cPacketBuilder builder = Version2cPacketBuilder.getBulk(community, instanceId, requestOid, BULK_SIZE);
					ByteBuffer b = builder.getBuffer();
					LOGGER.trace("Writing GETBULK: {} #{} ({}), packet size = {}", requestOid, instanceId, community, b.remaining());
					write.handle(address, b);
					break;
				}
				default:
					break;
				}
			} else {
				authRemoteEnginePendingRequestManager.registerPendingRequest(new AuthRemoteEnginePendingRequestManager.PendingRequest(shouldRepeatWhat, instanceId, requestOid));
				authRemoteEnginePendingRequestManager.sendPendingRequestsIfReady(address, write);
			}
		}
		
		private void fail(IOException e) {
			LOGGER.trace("Failed ({}:{})", address, requestOid, e);
			shouldRepeatWhat = 0;
			requestOid = null;
			SnmpClientHandler.Callback.GetCallback c = callback;
			callback = null;
			c.failed(e);
		}
		
		private void handle(int errorStatus, int errorIndex, Iterable<Result> results) {
			if (callback == null) {
				LOGGER.trace("Received more but finished");
				return;
			}
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

			/*%%%%%%%%%
			if (errorStatus == BerConstants.ERROR_STATUS_RETRY) {
				String newSendState = shouldRepeatWhat + "/" + requestOid + "/" + BaseEncoding.base64().encode(authEngine.getId()) + "/" + authEngine.getBootCount() + "/" + authEngine.getTime();
				if (!newSendState.equals(sendState)) {
					sendState = newSendState;
					instanceMapper.map(this);
					LOGGER.trace("Repeating after receiving auth engine completion message ({}) --> {}", instanceId, sendState);
					sendTimestamp = DateUtils.now();
					write();
				} else {
					instanceMapper.remap(this);
					LOGGER.trace("NOT repeating after receiving auth engine completion message ({}) --> {}", instanceId, newSendState);
				}
				return;
			}
			*/

			if (errorStatus != 0) {
				LOGGER.trace("Received error: {}/{} ({}:{})", errorStatus, errorIndex, address, requestOid);
			}

			if (shouldRepeatWhat == BerConstants.GET) {
				Result found = null;
				for (Result r : results) {
					if (requestOid.equals(r.getOid())) {
						LOGGER.trace("Scalar found: {}", r);
						found = r;
					}
				}
				if (found != null) {
					requestOid = null;
					SnmpClientHandler.Callback.GetCallback c = callback;
					callback = null;
					c.result(found);
					c.close();
					return;
				} else {
					instanceMapper.map(this);
					sendTimestamp = DateUtils.now();
					shouldRepeatWhat = BerConstants.GETBULK;
					write();
				}
			} else {
				Oid lastOid = null;
				for (Result r : results) {
					LOGGER.trace("Received in bulk: {} ({}:{})", r, address, requestOid);
				}
				for (Result r : results) {
					if (!initialRequestOid.isPrefixOf(r.getOid())) {
						LOGGER.trace("{} not prefixed by {} ({}:{})", r.getOid(), initialRequestOid, address, requestOid);
						lastOid = null;
						break;
					}
					LOGGER.trace("Addind to results: {} ({}:{})", r, address, requestOid);
					if ((GET_LIMIT > 0) && (countResults >= GET_LIMIT)) {
						LOGGER.warn("{} reached limit ({}:{})", requestOid, address, requestOid);
						lastOid = null;
						break;
					}
					countResults++;
					callback.result(r);
					lastOid = r.getOid();
				}
				if (lastOid != null) {
					LOGGER.trace("Continuing from: {} ({}:{})", lastOid, address, requestOid);
					
					requestOid = lastOid;
					
					instanceMapper.map(this);
					sendTimestamp = DateUtils.now();
					shouldRepeatWhat = BerConstants.GETBULK;
					write();
				} else {
					LOGGER.trace("Stop ({}:{})", address, requestOid);
					// Stop here
					requestOid = null;
					SnmpClientHandler.Callback.GetCallback c = callback;
					callback = null;
					c.close();
				}
			}
		}
	}
}
