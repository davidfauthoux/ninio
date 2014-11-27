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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.util.DateUtils;

public final class SnmpClient implements Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpClient.class);

	private final SnmpClientConfigurator configurator;
	private final RequestIdProvider requestIdProvider = new RequestIdProvider();
	private final Set<InstanceMapper> instanceMappers = new HashSet<>();
	private volatile boolean closed = false;

	public SnmpClient(final SnmpClientConfigurator configurator) {
		this.configurator = configurator;
		
		configurator.repeatExecutor.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				if (closed) {
					throw new RuntimeException("Stop requested");
				}

				configurator.queue.post(new Runnable() {
					@Override
					public void run() {
						Date now = new Date();
						for (InstanceMapper i : instanceMappers) {
							i.repeat(now);
						}
					}
				});
			}
		}, 0, (long) (configurator.repeatTime * 1000d), TimeUnit.MILLISECONDS);
	}
	
	@Override
	public void close() {
		closed = true;
	}
	
	private static final Random RANDOM = new Random(System.currentTimeMillis());

	private static final class RequestIdProvider {
		private static final int LOOP_REQUEST_ID = 1 << 16; // 2^16
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
		configurator.queue.post(new Runnable() {
			@Override
			public void run() {
				Ready ready = configurator.readyFactory.create(configurator.queue);
				
				final InstanceMapper instanceMapper = new InstanceMapper(configurator.address, requestIdProvider);
				instanceMappers.add(instanceMapper);

				ready.connect(configurator.address, new ReadyConnection() {
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						int instanceId;
						int errorStatus;
						int errorIndex;
						Iterable<Result> results;
						try {
							if (configurator.authEngine == null) {
								Version2cPacketParser parser = new Version2cPacketParser(buffer);
								instanceId = parser.getRequestId();
								errorStatus = parser.getErrorStatus();
								errorIndex = parser.getErrorIndex();
								results = parser.getResults();
							} else {
								Version3PacketParser parser = new Version3PacketParser(configurator.authEngine, buffer);
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
						if (instanceMappers.remove(instanceMapper)) {
							clientHandler.failed(e);
						}
					}
					
					@Override
					public void connected(final FailableCloseableByteBufferHandler write) {
						final SnmpWriter w = new SnmpWriter(write, configurator.community, configurator.authEngine);
						
						clientHandler.launched(new SnmpClientHandler.Callback() {
							@Override
							public void close() {
								if (instanceMappers.remove(instanceMapper)) {
									instanceMapper.closedByUser();
								}
								
								write.close();
							}
							@Override
							public void get(Oid oid, GetCallback callback) {
								Instance i = new Instance(instanceMapper, callback, w, oid, configurator);
								instanceMapper.map(i);
								w.get(i.instanceId, oid);
							}
						});
					}
					
					@Override
					public void close() {
						if (instanceMappers.remove(instanceMapper)) {
							instanceMapper.closedByPeer();
						}
					}
				});
			}
		});
	}
	
	private static final class InstanceMapper {
		private final Address address;
		private final Map<Integer, Instance> instances = new HashMap<>();
		private RequestIdProvider requestIdProvider;
		
		public InstanceMapper(Address address, RequestIdProvider requestIdProvider) {
			this.address = address;
			this.requestIdProvider = requestIdProvider;
		}
		
		public void map(Instance instance) {
			int instanceId = requestIdProvider.get();

			if (instances.containsKey(instanceId)) {
				LOGGER.warn("The maximum number of simultaneous request has been reached [{}]", address);
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
		private final SnmpClientConfigurator configurator;
		private final Date beginningTimestamp = new Date();
		private Date receptionTimestamp = null;
		private Date sendTimestamp = new Date();
		private int shouldRepeatWhat = 0;
		public int instanceId;
		private final double repeatRandomizationRandomized;

		public Instance(InstanceMapper instanceMapper, SnmpClientHandler.Callback.GetCallback callback, SnmpWriter write, Oid requestOid, SnmpClientConfigurator configurator) {
			this.instanceMapper = instanceMapper;
			this.callback = callback;
			this.write = write;
			this.requestOid = requestOid;
			initialRequestOid = requestOid;
			this.configurator = configurator;
			
			repeatRandomizationRandomized = (RANDOM.nextDouble() * configurator.repeatRandomization) - (1d / 2d); // [ -0.5, 0.5 [
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
			
			if ((n - DateUtils.from(beginningTimestamp)) >= (configurator.timeoutFromBeginning)) {
				shouldRepeatWhat = -1;
				requestOid = null;
				SnmpClientHandler.Callback.GetCallback c = callback;
				callback = null;
				//%% allResults = null;
				c.failed(new IOException("Timeout from beginning"));
				return;
			}

			if (receptionTimestamp != null) {
				if ((n - DateUtils.from(receptionTimestamp)) >= configurator.timeoutFromLastReception) {
					SnmpClientHandler.Callback.GetCallback c = callback;
					callback = null;
					//%% allResults = null;
					c.failed(new IOException("Timeout from last reception"));
					return;
				}
			}

			if ((n - DateUtils.from(sendTimestamp)) >= (configurator.minTimeToRepeat + repeatRandomizationRandomized)) {
				LOGGER.trace("Repeating {} {}", instanceMapper.address, requestOid);
				switch (shouldRepeatWhat) { 
				case 0:
					write.get(instanceId, requestOid);
					break;
				case 1:
					write.getNext(instanceId, requestOid);
					break;
				case 2:
					write.getBulk(instanceId, requestOid, configurator.bulkSize);
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
						if ((configurator.getLimit > 0) && (countResults >= configurator.getLimit)) {
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
						write.getBulk(instanceId, requestOid, configurator.bulkSize);
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
