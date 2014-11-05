package com.davfx.ninio.snmp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.DatagramReadyFactory;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.util.ConfigUtils;
import com.davfx.util.DateUtils;
import com.typesafe.config.Config;

public final class SnmpClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpClient.class);

	private static final Config CONFIG = ConfigUtils.load(SnmpClient.class);
	public static final int DEFAULT_PORT = 161;

	private Queue queue = null;
	private String community = "community";
	private AuthRemoteEngine authEngine = null;
	private Address address = new Address("localhost", DEFAULT_PORT);
	private String host = null;
	private int port = -1;
	private int bulkSize = CONFIG.getInt("snmp.bulkSize");
	private double minTimeToRepeat = ConfigUtils.getDuration(CONFIG, "snmp.minTimeToRepeat");
	private int getLimit = CONFIG.getInt("snmp.getLimit");;

	private double repeatTime = ConfigUtils.getDuration(CONFIG, "snmp.repeatTime");
	private ScheduledExecutorService repeatExecutor = null;

	private double timeoutFromBeginning = ConfigUtils.getDuration(CONFIG, "snmp.timeoutFromBeginning");
	private double timeoutFromLastReception = ConfigUtils.getDuration(CONFIG, "snmp.timeoutFromLastReception");
	
	private double repeatRandomization = ConfigUtils.getDuration(CONFIG, "snmp.repeatRandomization");
	
	private ReadyFactory readyFactory = new DatagramReadyFactory();

	public SnmpClient() {
	}
	
	public SnmpClient withMinTimeToRepeat(double minTimeToRepeat) {
		this.minTimeToRepeat = minTimeToRepeat;
		return this;
	}
	public SnmpClient withRepeatTime(double repeatTime) {
		this.repeatTime = repeatTime;
		return this;
	}

	public SnmpClient withTimeoutFromBeginning(double timeoutFromBeginning) {
		this.timeoutFromBeginning = timeoutFromBeginning;
		return this;
	}
	public SnmpClient withTimeoutFromLastReception(double timeoutFromLastReception) {
		this.timeoutFromLastReception = timeoutFromLastReception;
		return this;
	}

	public SnmpClient withRepeatRandomization(double repeatRandomization) {
		this.repeatRandomization = repeatRandomization;
		return this;
	}

	public SnmpClient withQueue(Queue queue, ScheduledExecutorService repeatExecutor) {
		this.queue = queue;
		this.repeatExecutor = repeatExecutor;
		return this;
	}
	public SnmpClient withCommunity(String community) {
		this.community = community;
		return this;
	}
	public SnmpClient withLoginPassword(String authLogin, String authPassword, String authDigestAlgorithm, String privLogin, String privPassword, String privEncryptionAlgorithm) {
		authEngine = new AuthRemoteEngine(authLogin, authPassword, authDigestAlgorithm, privLogin, privPassword, privEncryptionAlgorithm);
		return this;
	}
	
	public SnmpClient withHost(String host) {
		this.host = host;
		return this;
	}
	public SnmpClient withPort(int port) {
		this.port = port;
		return this;
	}
	public SnmpClient withAddress(Address address) {
		this.address = address;
		return this;
	}
	
	public SnmpClient withBulkSize(int bulkSize) {
		this.bulkSize = bulkSize;
		return this;
	}
	public SnmpClient withGetLimit(int getLimit) {
		this.getLimit = getLimit;
		return this;
	}
	
	public SnmpClient override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
	
	private static final Random RANDOM = new Random(System.currentTimeMillis());

	private static final class RequestIdProvider {
		private static final int LOOP_REQUEST_ID = 2 ^ 16;
		private static final AtomicInteger PREFIX = new AtomicInteger(RANDOM.nextInt());
		
		private final int prefix = PREFIX.getAndIncrement();
		private int nextRequestId = 0;

		public RequestIdProvider() {
		}
		
		public int get() {
			int id = ((prefix & 0xFFFF) << 16) | (nextRequestId & 0xFFFF);
			nextRequestId++;
			if (nextRequestId == LOOP_REQUEST_ID) {
				nextRequestId = 0;
			}
			return id;
		}
	}
	
	public void connect(final SnmpClientHandler clientHandler) {
		final Queue q;
		final ScheduledExecutorService re;
		final boolean shouldCloseQueue;
		if (queue == null) {
			try {
				q = new Queue();
			} catch (IOException e) {
				clientHandler.failed(e);
				return;
			}
			re = Executors.newSingleThreadScheduledExecutor();
			shouldCloseQueue = true;
		} else {
			q = queue;
			re = repeatExecutor;
			shouldCloseQueue = false;
		}

		final Address a;
		if (host != null) {
			if (port < 0) {
				a = new Address(host, address.getPort());
			} else {
				a = new Address(host, port);
			}
		} else {
			a = address;
		}
		
		final RequestIdProvider requestIdProvider = new RequestIdProvider();
		
		final Set<InstanceMapper> instanceMappers = new HashSet<>();
		re.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				q.post(new Runnable() {
					@Override
					public void run() {
						Date now = new Date();
						for (InstanceMapper i : instanceMappers) {
							i.repeat(now);
						}
					}
				});
			}
		}, 0, (long) (repeatTime * 1000d), TimeUnit.MILLISECONDS);

		q.post(new Runnable() {
			@Override
			public void run() {
				Ready ready = readyFactory.create(q);
				
				ready.connect(a, new ReadyConnection() {
					private final InstanceMapper instanceMapper = new InstanceMapper(requestIdProvider);

					@Override
					public void handle(Address address, ByteBuffer buffer) {
						int instanceId;
						int errorStatus;
						int errorIndex;
						Iterable<Result> results;
						try {
							if (authEngine == null) {
								Version2cPacketParser parser = new Version2cPacketParser(buffer);
								instanceId = parser.getRequestId();
								errorStatus = parser.getErrorStatus();
								errorIndex = parser.getErrorIndex();
								results = parser.getResults();
							} else {
								Version3PacketParser parser = new Version3PacketParser(authEngine, buffer);
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
					
					@Override
					public void failed(IOException e) {
						if (shouldCloseQueue) {
							re.shutdown();
							q.close();
						}
						if (instanceMappers.remove(instanceMapper)) {
							clientHandler.failed(e);
						}
					}
					
					@Override
					public void connected(final FailableCloseableByteBufferHandler write) {
						instanceMappers.add(instanceMapper);
						
						final SnmpWriter w = new SnmpWriter(write, community, authEngine);
						
						clientHandler.launched(new SnmpClientHandler.Callback() {
							@Override
							public void close() {
								if (instanceMappers.remove(instanceMapper)) {
									instanceMapper.closedByUser();
								}
								
								write.close();

								if (shouldCloseQueue) {
									re.shutdown();
									q.close();
								}
							}
							@Override
							public void get(Oid oid, GetCallback callback) {
								Instance i = new Instance(instanceMapper, callback, w, oid, getLimit, bulkSize, minTimeToRepeat, timeoutFromBeginning, timeoutFromLastReception, repeatRandomization);
								instanceMapper.map(i);
								w.get(i.instanceId, oid);
							}
						});
					}
					
					@Override
					public void close() {
						if (shouldCloseQueue) {
							re.shutdown();
							q.close();
						}
						if (instanceMappers.remove(instanceMapper)) {
							instanceMapper.closedByPeer();
						}
					}
				});
			}
		});
	}
	
	private static final class InstanceMapper {
		private final Map<Integer, Instance> instances = new HashMap<>();
		private RequestIdProvider requestIdProvider;
		
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
			
			instance.instanceId = instanceId;
		}
		
		public void closedByUser() {
			instances.clear();
		}
		public void closedByPeer() {
			for (Instance i : instances.values()) {
				i.closedByPeer();
			}
			instances.clear();
		}
		
		public void handle(int instanceId, int errorStatus, int errorIndex, Iterable<Result> results) {
			Instance i = instances.remove(instanceId);
			if (i == null) {
				return;
			}
			i.handle(errorStatus, errorIndex, results);
		}
		
		public void repeat(Date now) {
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
	
	private static final class SnmpWriter {
		private final CloseableByteBufferHandler write;
		private final String community;
		private final AuthRemoteEngine authEngine;
		public SnmpWriter(CloseableByteBufferHandler write, String community, AuthRemoteEngine authEngine) {
			this.write = write;
			this.community = community;
			this.authEngine = authEngine;
		}
		
		public void get(int instanceId, Oid oid) {
			if (authEngine == null) {
				Version2cPacketBuilder builder = Version2cPacketBuilder.get(community, instanceId, oid);
				LOGGER.trace("Writing GET: {} #{} ({})", oid, instanceId, community);
				write.handle(null, builder.getBuffer());
			} else {
				Version3PacketBuilder builder = Version3PacketBuilder.get(authEngine, instanceId, oid);
				write.handle(null, builder.getBuffer());
			}
		}
		public void getNext(int instanceId, Oid oid) {
			if (authEngine == null) {
				Version2cPacketBuilder builder = Version2cPacketBuilder.getNext(community, instanceId, oid);
				LOGGER.trace("Writing GETNEXT: {} #{} ({})", oid, instanceId, community);
				write.handle(null, builder.getBuffer());
			} else {
				Version3PacketBuilder builder = Version3PacketBuilder.getNext(authEngine, instanceId, oid);
				write.handle(null, builder.getBuffer());
			}
		}
		public void getBulk(int instanceId, Oid oid, int bulkLength) {
			if (authEngine == null) {
				Version2cPacketBuilder builder = Version2cPacketBuilder.getBulk(community, instanceId, oid, bulkLength);
				LOGGER.trace("Writing GETBULK: {} #{} ({})", oid, instanceId, community);
				write.handle(null, builder.getBuffer());
			} else {
				Version3PacketBuilder builder = Version3PacketBuilder.getBulk(authEngine, instanceId, oid, bulkLength);
				write.handle(null, builder.getBuffer());
			}
		}
	}
	
	private static final class Instance {
		private final InstanceMapper instanceMapper;
		private SnmpClientHandler.Callback.GetCallback callback;
		private final SnmpWriter write;
		private final Oid initialRequestOid;
		private Oid requestOid;
		//%% private List<Result> allResults = null;
		private int countResults = 0;
		private final int getLimit;
		private final int bulkSize;
		private final Date beginningTimestamp = new Date();
		private Date receptionTimestamp = null;
		private Date sendTimestamp = new Date();
		private int shouldRepeatWhat = 0;
		public int instanceId;
		private final double minTimeToRepeat;
		private final double timeoutFromBeginning;
		private final double timeoutFromLastReception;
		private final double repeatRandomizationRandomized;

		public Instance(InstanceMapper instanceMapper, SnmpClientHandler.Callback.GetCallback callback, SnmpWriter write, Oid requestOid,
				int getLimit, int bulkSize,
				double minTimeToRepeat, double timeoutFromBeginning, double timeoutFromLastReception, double repeatRandomization) {
			this.instanceMapper = instanceMapper;
			this.callback = callback;
			this.write = write;
			this.requestOid = requestOid;
			this.getLimit = getLimit;
			this.bulkSize = bulkSize;
			initialRequestOid = requestOid;
			
			this.minTimeToRepeat = minTimeToRepeat;
			this.timeoutFromBeginning = timeoutFromBeginning;
			this.timeoutFromLastReception = timeoutFromLastReception;
			repeatRandomizationRandomized = (RANDOM.nextDouble() * repeatRandomization) - (1d / 2d); // [ -0.5, 0.5 [
		}
		
		public void closedByPeer() {
			if (callback == null) {
				return;
			}
			if (requestOid == null) {
				return;
			}
			
			shouldRepeatWhat = -1;
			requestOid = null;
			SnmpClientHandler.Callback.GetCallback c = callback;
			callback = null;
			c.failed(new IOException("Closed by peer"));
		}
		
		public void repeat(Date now) {
			if (callback == null) {
				return;
			}
			if (requestOid == null) {
				return;
			}
			
			double n = DateUtils.from(now);
			
			if ((n - DateUtils.from(beginningTimestamp)) >= (timeoutFromBeginning)) {
				shouldRepeatWhat = -1;
				requestOid = null;
				SnmpClientHandler.Callback.GetCallback c = callback;
				callback = null;
				//%% allResults = null;
				c.failed(new IOException("Timeout from beginning"));
				return;
			}

			if (receptionTimestamp != null) {
				if ((n - DateUtils.from(receptionTimestamp)) >= timeoutFromLastReception) {
					SnmpClientHandler.Callback.GetCallback c = callback;
					callback = null;
					//%% allResults = null;
					c.failed(new IOException("Timeout from last reception"));
					return;
				}
			}

			if ((n - DateUtils.from(sendTimestamp)) >= (minTimeToRepeat + repeatRandomizationRandomized)) {
				LOGGER.debug("Repeating {}", requestOid);
				switch (shouldRepeatWhat) { 
				case 0:
					write.get(instanceId, requestOid);
					break;
				case 1:
					write.getNext(instanceId, requestOid);
					break;
				case 2:
					write.getBulk(instanceId, requestOid, bulkSize);
					break;
				default:
					break;
				}
			}
			return;
		}
		
		private void handle(int errorStatus, int errorIndex, Iterable<Result> results) {
			if (callback == null) {
				LOGGER.trace("Received more but finished");
				return;
			}
			if (requestOid == null) {
				return;
			}

			receptionTimestamp = new Date();
			
			if (errorStatus == BerConstants.ERROR_STATUS_AUTHENTICATION_FAILED) {
				//%% shouldRepeatWhat = -1;
				requestOid = null;
				SnmpClientHandler.Callback.GetCallback c = callback;
				callback = null;
				//%% allResults = null;
				c.failed(new IOException("Authentication failed"));
				return;
			}
			
			if (shouldRepeatWhat == 0) {
				if (errorStatus == BerConstants.ERROR_STATUS_RETRY) {
					LOGGER.trace("Retrying GET after receiving auth engine completion message");
					instanceMapper.map(this);
					sendTimestamp = new Date();
					write.get(instanceId, requestOid);
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
							//%% shouldRepeatWhat = -1;
							requestOid = null;
							SnmpClientHandler.Callback.GetCallback c = callback;
							callback = null;
							//%% allResults = null;
							c.result(found);
							c.close();
							return;
						}
					}
					if (fallback) {
						//%% allResults = new LinkedList<>();
						instanceMapper.map(this);
						sendTimestamp = new Date();
						shouldRepeatWhat = 1;
						write.getNext(instanceId, requestOid);
					}
				}
			} else {
				if (errorStatus != 0) {
					//%% shouldRepeatWhat = -1;
					requestOid = null;
					SnmpClientHandler.Callback.GetCallback c = callback;
					callback = null;
					//%% allResults = null;
					c.failed(new IOException("Request failed with error: " + errorStatus + "/" + errorIndex));
				} else {
					Oid lastOid = null;
					for (Result r : results) {
						LOGGER.trace("Received in bulk: {}", r);
					}
					for (Result r : results) {
						if (r.getValue() == null) {
							continue;
						}
						if (!initialRequestOid.isPrefix(r.getOid())) {
							LOGGER.trace("{} not prefixed by {}", r.getOid(), initialRequestOid);
							lastOid = null;
							break;
						}
						LOGGER.trace("Addind to results: {}", r);
						if ((getLimit > 0) && (countResults >= getLimit)) {
						//%% if ((getLimit > 0) && (allResults.size() == getLimit)) {
							LOGGER.warn("{} reached limit", requestOid);
							lastOid = null;
							break;
						}
						countResults++;
						callback.result(r);
						//%% allResults.add(r);
						lastOid = r.getOid();
					}
					if (lastOid != null) {
						LOGGER.trace("Continuing from: {}", lastOid);
						
						requestOid = lastOid;
						
						instanceMapper.map(this);
						sendTimestamp = new Date();
						shouldRepeatWhat = 2;
						write.getBulk(instanceId, requestOid, bulkSize);
					} else {
						// Stop here
						//%% shouldRepeatWhat = -1;
						requestOid = null;
						SnmpClientHandler.Callback.GetCallback c = callback;
						callback = null;
						/*%%
						List<Result> r = allResults;
						allResults = null;
						c.finished(r);
						*/
						c.close();
					}
				}
			}
		}
	}
}
