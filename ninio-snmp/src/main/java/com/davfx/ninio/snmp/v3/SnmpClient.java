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
import com.davfx.ninio.core.v3.InternalQueue;
import com.davfx.ninio.core.v3.Receiver;
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

	private static final int BULK_SIZE = CONFIG.getInt("ninio.snmp.bulkSize");
	private static final int GET_LIMIT = CONFIG.getInt("ninio.snmp.getLimit");
	private static final double AUHT_ENGINES_CACHE_DURATION = ConfigUtils.getDuration(CONFIG, "ninio.snmp.auth.cache");
	
	private final Executor executor;
	private final Connector connector;
	private final RequestIdProvider requestIdProvider = new RequestIdProvider();
	private final InstanceMapper instanceMapper = new InstanceMapper();
	private final Cache<Address, AuthRemoteEngine> authRemoteEngines = CacheBuilder.newBuilder().expireAfterWrite((long) (AUHT_ENGINES_CACHE_DURATION * 1000d), TimeUnit.MILLISECONDS).build();

	public SnmpClient() {
		this(InternalQueue.EXECUTOR, new DatagramConnectorFactory());
	}
	public SnmpClient(Executor executor, ConnectorFactory connectorFactory) {
		this.executor = executor;
		connector = connectorFactory.create();
		connector.receiving(new Receiver() {
			@Override
			public void received(Address address, ByteBuffer buffer) {
				LOGGER.trace("Received SNMP packet, size = {}", buffer.remaining());
				int instanceId;
				int errorStatus;
				int errorIndex;
				Iterable<Result> results;
				try {
					AuthRemoteEngine authRemoteEngine = authRemoteEngines.getIfPresent(address);
					if (authRemoteEngine == null) {
						Version2cPacketParser parser = new Version2cPacketParser(buffer);
						instanceId = parser.getRequestId();
						errorStatus = parser.getErrorStatus();
						errorIndex = parser.getErrorIndex();
						results = parser.getResults();
					} else {
						Version3PacketParser parser = new Version3PacketParser(authRemoteEngine, buffer);
						instanceId = parser.getRequestId();
						errorStatus = parser.getErrorStatus();
						errorIndex = parser.getErrorIndex();
						results = parser.getResults();
					}
				} catch (Exception e) {
					LOGGER.error("Invalid packet", e);
					return;
				}
				
				instanceMapper.handle(instanceId, errorStatus, errorIndex, results);
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
		connector.disconnect();
	}
	
	public SnmpRequest create() {
		return new SnmpRequest() {
			private SnmpReceiver receiver = null;
			private Failing failing = null;
			
			@Override
			public void receiving(SnmpReceiver receiver) {
				this.receiver = receiver;
			}
			@Override
			public void failing(Failing failing) {
				this.failing = failing;
			}
			
			@Override
			public void get(final Address address, final String community, final AuthRemoteSpecification authRemoteSpecification, final Oid oid) {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						AuthRemoteEngine authRemoteEngine = null;
						if (authRemoteSpecification != null) {
							authRemoteEngine = authRemoteEngines.getIfPresent(address);
							if (authRemoteEngine != null) {
								if (!authRemoteEngine.authRemoteSpecification.equals(authRemoteSpecification)) {
									authRemoteEngine = new AuthRemoteEngine(authRemoteSpecification);
								}
							} else {
								authRemoteEngine = new AuthRemoteEngine(authRemoteSpecification);
							}
						}
						if (authRemoteEngine != null) {
							authRemoteEngines.put(address, authRemoteEngine);
						}
						
						Instance i = new Instance(receiver, failing, oid, address, community, authRemoteEngine);
						instanceMapper.map(i);
						writeGet(address, i.instanceId, community, authRemoteEngine, oid);
					}
				});
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
	
	private final class InstanceMapper {
		private final Map<Integer, Instance> instances = new HashMap<>();
		
		public InstanceMapper() {
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
	
	private void writeGet(Address to, int instanceId, String community, AuthRemoteEngine authEngine, Oid oid) {
		if (authEngine == null) {
			Version2cPacketBuilder builder = Version2cPacketBuilder.get(community, instanceId, oid);
			ByteBuffer b = builder.getBuffer();
			LOGGER.trace("Writing GET: {} #{} ({}), packet size = {}", oid, instanceId, community, b.remaining());
			connector.send(to, b);
		} else {
			Version3PacketBuilder builder = Version3PacketBuilder.get(authEngine, instanceId, oid);
			ByteBuffer b = builder.getBuffer();
			LOGGER.trace("Writing GET v3: {} #{}, packet size = {}", oid, instanceId, b.remaining());
			connector.send(to, b);
		}
	}
	/*
	private void writeGetNext(Address to, int instanceId, String community, AuthRemoteEngine authEngine, Oid oid) {
		if (authEngine == null) {
			Version2cPacketBuilder builder = Version2cPacketBuilder.getNext(community, instanceId, oid);
			ByteBuffer b = builder.getBuffer();
			LOGGER.trace("Writing GETNEXT: {} #{} ({}), packet size = {}", oid, instanceId, community, b.remaining());
			connector.send(to, b);
		} else {
			Version3PacketBuilder builder = Version3PacketBuilder.getNext(authEngine, instanceId, oid);
			ByteBuffer b = builder.getBuffer();
			LOGGER.trace("Writing GETNEXT v3: {} #{}, packet size = {}", oid, instanceId, b.remaining());
			connector.send(to, b);
		}
	}
	*/
	private void writeGetBulk(Address to, int instanceId, String community, AuthRemoteEngine authEngine, Oid oid, int bulkLength) {
		if (authEngine == null) {
			Version2cPacketBuilder builder = Version2cPacketBuilder.getBulk(community, instanceId, oid, bulkLength);
			ByteBuffer b = builder.getBuffer();
			LOGGER.trace("Writing GETBULK: {} #{} ({}), packet size = {}", oid, instanceId, community, b.remaining());
			connector.send(to, b);
		} else {
			Version3PacketBuilder builder = Version3PacketBuilder.getBulk(authEngine, instanceId, oid, bulkLength);
			ByteBuffer b = builder.getBuffer();
			LOGGER.trace("Writing GETBULK v3: {} #{}, packet size = {}", oid, instanceId, b.remaining());
			connector.send(to, b);
		}
	}
	
	private final class Instance {
		private SnmpReceiver receiver;
		private Failing failing;
		
		private final Oid initialRequestOid;
		private Oid requestOid;
		private int countResults = 0;
		private int shouldRepeatWhat = BerConstants.GET;
		public int instanceId;

		private final Address address;
		private final String community;
		private final AuthRemoteEngine authEngine;

		public Instance(SnmpReceiver receiver, Failing failing, Oid requestOid, Address address, String community, AuthRemoteEngine authEngine) {
			this.receiver = receiver;
			this.failing = failing;

			this.requestOid = requestOid;
			initialRequestOid = requestOid;
			
			this.address = address;
			this.community = community;
			this.authEngine = authEngine;
		}
		
		public void close() {
			shouldRepeatWhat = 0;
			requestOid = null;
			receiver = null;
			failing = null;
		}
		
		private void handle(int errorStatus, int errorIndex, Iterable<Result> results) {
			if (requestOid == null) {
				return;
			}

			if (errorStatus == BerConstants.ERROR_STATUS_AUTHENTICATION_FAILED) {
				requestOid = null;
				failing.failed(new IOException("Authentication failed"));
				receiver = null; //TODO verif reentrance
				failing = null;
				return;
			}
			
			if (errorStatus == BerConstants.ERROR_STATUS_TIMEOUT) {
				requestOid = null;
				failing.failed(new IOException("Timeout"));
				receiver = null; //TODO verif reentrance
				failing = null;
				return;
			}
			
			if (shouldRepeatWhat == BerConstants.GET) {
				if (errorStatus == BerConstants.ERROR_STATUS_RETRY) {
					instanceMapper.map(this);
					LOGGER.trace("Retrying GET after receiving auth engine completion message ({})", instanceId);
					writeGet(address, instanceId, community, authEngine, requestOid);
				} else {
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
							receiver = null; //TODO verif reentrance
							failing = null;
							return;
						}
					}
					if (fallback) {
						instanceMapper.map(this);
						shouldRepeatWhat = BerConstants.GETBULK;
						writeGetBulk(address, instanceId, community, authEngine, requestOid, BULK_SIZE);
					}
				}
			} else {
				if (errorStatus != 0) {
					requestOid = null;
					receiver.finished();
					receiver = null; //TODO verif reentrance
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
						writeGetBulk(address, instanceId, community, authEngine, requestOid, BULK_SIZE);
					} else {
						// Stop here
						requestOid = null;
						receiver.finished();
						receiver = null; //TODO verif reentrance
						failing = null;
					}
				}
			}
		}
	}
}
