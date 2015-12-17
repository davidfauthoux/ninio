package com.davfx.ninio.snmp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.QueueReady;
import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.util.QueueScheduled;
import com.davfx.util.ConfigUtils;
import com.davfx.util.DateUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

//TODO SNMP v3: Use community (v2 format) prefixed with ':' -> Register AuthEngine in a map. Exchange in v3 format. Reply with responses in v2 format.

// Transforms all values to string
// Does not tell community on response
@Deprecated
public final class InternalSimpleSnmpCacheServerReadyFactory implements ReadyFactory, Closeable, AutoCloseable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(InternalSimpleSnmpCacheServerReadyFactory.class);

	private static final Config CONFIG = ConfigFactory.load(InternalSimpleSnmpCacheServerReadyFactory.class.getClassLoader());

	private static final double CACHE_EXPIRATION = ConfigUtils.getDuration(CONFIG, "ninio.snmp.cache.expiration");
	private static final double CACHE_TIMEOUT = ConfigUtils.getDuration(CONFIG, "ninio.snmp.cache.timeout");
	private static final double CHECK_TIME = ConfigUtils.getDuration(CONFIG, "ninio.snmp.cache.check");
	private static final double REPEAT_TIME = ConfigUtils.getDuration(CONFIG, "ninio.snmp.cache.repeat");

	private static final String NO_COMMUNITY = "xxx";
	
	public static interface Filter {
		boolean cache(Address address, Oid oid);
	}
	
	private final Queue queue;
	private final ReadyFactory wrappee;
	
	private final Filter filter;
	private final Map<Key, CacheElement> cache = new HashMap<>();
	private final Map<Integer, Key> fromRequestId = new HashMap<>();

	private final Closeable closeable;
	
	public InternalSimpleSnmpCacheServerReadyFactory(Filter filter, Queue queue, ReadyFactory wrappee) {
		this.queue = queue;
		this.wrappee = wrappee;
		this.filter = filter;
		
		closeable = QueueScheduled.schedule(queue, CHECK_TIME, new Runnable() {
			@Override
			public void run() {
				LOGGER.trace("Checking {}", CHECK_TIME);
				double now = DateUtils.now();
				
				Iterator<Map.Entry<Key, CacheElement>> i = cache.entrySet().iterator();
				while (i.hasNext()) {
					Map.Entry<Key, CacheElement> e = i.next();
					Key key = e.getKey();
					CacheElement cacheElement = e.getValue();
					
					if ((cacheElement.requesting == null) && cacheElement.expired(now)) {
						LOGGER.trace("Expired: {}/{}", key.request, key.oid);
						i.remove();
						continue;
					}
					
					if (cacheElement.requesting != null) {
						if (cacheElement.timeout(now)) {
							cacheElement.errorStatus = BerConstants.ERROR_STATUS_TIMEOUT;
							cacheElement.errorIndex = 0;
							cacheElement.results = null;
							LOGGER.trace("Timeout: {}/{}", key.request, key.oid);
							for (Requesting r : cacheElement.requesting) {
								ByteBuffer builtBuffer = build(r.requestId, NO_COMMUNITY, cacheElement.errorStatus, cacheElement.errorIndex, cacheElement.results);
								r.connection.handle(null, builtBuffer);
							}
							cacheElement.requesting = null;
						}

						/*%%%
						Iterator<Requesting> j = cacheElement.requesting.iterator();
						while (j.hasNext()) {
							Requesting r = j.next();
							if (r.expired(now)) {
								LOGGER.trace("Timeout: {}", r.requestId);
								ByteBuffer builtBuffer = build(r.requestId, NO_COMMUNITY, BerConstants.ERROR_STATUS_TIMEOUT, 0, null);
								r.connection.handle(null, builtBuffer);
								j.remove();
							}
						}
						*/
					}
				}
			}
		});
	}
	
	@Override
	public void close() {
		closeable.close();
	}
	
	private static final class Key {
		public final Address address;
		public final int request;
		public final Oid oid;
		public Key(Address address, int request, Oid oid) {
			this.address = address;
			this.request = request;
			this.oid = oid;
		}
		@Override
		public int hashCode() {
			return Objects.hash(address, oid, request);
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (!(obj instanceof Key)) {
				return false;
			}
			Key other = (Key) obj;
			return address.equals(other.address) && (request == other.request) && oid.equals(other.oid);
		}
	}
	
	private static final class Requesting {
		public final int requestId;
		public final ReadyConnection connection;
		
		public Requesting(int requestId, ReadyConnection connection) {
			this.requestId = requestId;
			this.connection = connection;
		}
	}
	
	private static final class CacheElement {
		public List<Requesting> requesting = new LinkedList<>();
		
		public int errorStatus;
		public int errorIndex;
		public Iterable<Result> results;
		
		private final double timestamp = DateUtils.now();
		private double repeatTimestamp;
		
		public CacheElement() {
			repeatTimestamp = timestamp;
		}
		
		public boolean shouldReapeat(double now) {
			if ((now - repeatTimestamp) > REPEAT_TIME) {
				repeatTimestamp += REPEAT_TIME;
				return true;
			}
			return false;
		}

		public boolean expired(double now) {
			return (now - timestamp) > CACHE_EXPIRATION;
		}

		public boolean timeout(double now) {
			return (now - timestamp) > CACHE_TIMEOUT;
		}
	}
	
	@Override
	public Ready create() {
		LOGGER.trace("Ready created");
		final Ready wrappeeReady = wrappee.create();
		return new QueueReady(queue, new Ready() {
			@Override
			public void connect(final Address address, final ReadyConnection connection) {
				// final ReadyConnection connection = new MayBeClosedReadyConnection(sourceConnection);

				LOGGER.trace("Connecting to: {}", address);
				
				wrappeeReady.connect(address, new ReadyConnection() {
					@Override
					public void failed(IOException e) {
						connection.failed(e);
					}
					
					@Override
					public void connected(final FailableCloseableByteBufferHandler write) {
						connection.connected(new FailableCloseableByteBufferHandler() {
							@Override
							public void failed(IOException e) {
								write.failed(e);
							}
							@Override
							public void close() {
								write.close();
							}
							@Override
							public void handle(Address address, ByteBuffer sourceBuffer) {
								ByteBuffer buffer = sourceBuffer.duplicate();
								int requestId;
								String community;
								int request;
								final Oid oid;
								try {
									BerReader ber = new BerReader(buffer);
									ber.beginReadSequence();
									{
										ber.readInteger(); // Version
										community = BerPacketUtils.string(ber.readBytes());
										request = ber.beginReadSequence();
										{
											requestId = ber.readInteger();
											ber.readInteger(); // Non-repeater, not used
											ber.readInteger(); // Bulk length (ignored)
											ber.beginReadSequence();
											{
												ber.beginReadSequence();
												{
													oid = ber.readOid();
													ber.readNull();
												}
												ber.endReadSequence();
											}
											ber.endReadSequence();
										}
										ber.endReadSequence();
									}
									ber.endReadSequence();
								} catch (IOException e) {
									LOGGER.error("Invalid packet", e);
									return;
								}

								LOGGER.trace("Request {} with community: {} and oid: {}", requestId, community, oid);
								
								if (!filter.cache(address, oid)) {
									write.handle(address, sourceBuffer);
									return;
								}
								
								double now = DateUtils.now();
								
								Key key = new Key(address, request, oid);
								CacheElement cacheElement = cache.get(key);
								if (cacheElement != null) {
									if ((cacheElement.requesting == null) && cacheElement.expired(now)) {
										LOGGER.trace("Expired: {}/{}", request, oid);
										cacheElement = null;
									}
								}
								if (cacheElement == null) {
									cacheElement = new CacheElement();
									cache.put(key, cacheElement);

									fromRequestId.put(requestId, key);
									cacheElement.requesting.add(new Requesting(requestId, connection));
									
									LOGGER.trace("Request {} actually sent: {}/{}", requestId, request, oid);
									write.handle(address, sourceBuffer);
									return;
								}
								if (cacheElement.requesting == null) {
									LOGGER.trace("Request {} from cache: {}/{}", requestId, request, oid);
									ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, cacheElement.errorStatus, cacheElement.errorIndex, cacheElement.results);
									connection.handle(address, builtBuffer);
								} else {
									if (cacheElement.shouldReapeat(now)) {
										LOGGER.trace("Request {} repeated: {}/{}", requestId, request, oid);
										write.handle(address, sourceBuffer);
									} else {
										LOGGER.trace("Request {} not sent: {}/{}", requestId, request, oid);
									}
									if (fromRequestId.put(requestId, key) == null) {
										cacheElement.requesting.add(new Requesting(requestId, connection));
									}
								}
							}
						});
					}

					@Override
					public void handle(Address address, ByteBuffer sourceBuffer) {
						ByteBuffer buffer = sourceBuffer.duplicate();
						int requestId;
						int errorStatus;
						int errorIndex;
						Iterable<Result> results;
						try {
							Version2cPacketParser parser = new Version2cPacketParser(buffer);
							requestId = parser.getRequestId();
							errorStatus = parser.getErrorStatus();
							errorIndex = parser.getErrorIndex();
							results = parser.getResults();
						} catch (Exception e) {
							LOGGER.error("Invalid packet", e);
							return;
						}
						
						LOGGER.trace("Response for {}: {}", requestId, results);
						
						Key key = fromRequestId.remove(requestId);
						if (key == null) {
							return;
						}
						
						CacheElement cacheElement = cache.get(key);
						if (cacheElement == null) {
							return;
						}
						
						cacheElement.errorStatus = errorStatus;
						cacheElement.errorIndex = errorIndex;
						cacheElement.results = results;
						LOGGER.trace("Response for {} in cache", requestId);
						for (Requesting r : cacheElement.requesting) {
							ByteBuffer builtBuffer = build(r.requestId, NO_COMMUNITY, cacheElement.errorStatus, cacheElement.errorIndex, cacheElement.results);
							connection.handle(address, builtBuffer);
						}
						cacheElement.requesting = null;
					}
					
					@Override
					public void close() {
						connection.close();
					}
				});
			}
		});
	}
	
	private static ByteBuffer build(int requestId, String community, int errorStatus, int errorIndex, Iterable<Result> result) {
		SequenceBerPacket oidSequence = new SequenceBerPacket(BerConstants.SEQUENCE);
		if (result != null) {
			for (Result ov : result) {
				oidSequence.add(new SequenceBerPacket(BerConstants.SEQUENCE)
					.add(new OidBerPacket(ov.getOid()))
					.add(new BytesBerPacket(BerPacketUtils.bytes(ov.getValue()))));
			}
		}

		SequenceBerPacket root = new SequenceBerPacket(BerConstants.SEQUENCE)
		.add(new IntegerBerPacket(BerConstants.VERSION_2C))
		.add(new BytesBerPacket(BerPacketUtils.bytes(community)))
		.add(new SequenceBerPacket(BerConstants.RESPONSE)
			.add(new IntegerBerPacket(requestId))
			.add(new IntegerBerPacket(errorStatus))
			.add(new IntegerBerPacket(errorIndex))
			.add(oidSequence));

		ByteBuffer buffer = ByteBuffer.allocate(BerPacketUtils.typeAndLengthBufferLength(root.lengthBuffer()) + root.length());
		root.write(buffer);
		buffer.flip();

		return buffer;
	}
	
	/* Useless
	private static final class MayBeClosedReadyConnection implements ReadyConnection {
		private ReadyConnection wrappee;
		public MayBeClosedReadyConnection(ReadyConnection wrappee) {
			this.wrappee = wrappee;
		}
		
		@Override
		public void connected(FailableCloseableByteBufferHandler write) {
			if (wrappee == null) {
				return;
			}
			wrappee.connected(write);
		}
		@Override
		public void handle(Address address, ByteBuffer buffer) {
			if (wrappee == null) {
				return;
			}
			wrappee.handle(address, buffer);
		}
		@Override
		public void close() {
			if (wrappee == null) {
				return;
			}
			wrappee.close();
			wrappee = null;
		}
		@Override
		public void failed(IOException e) {
			if (wrappee == null) {
				return;
			}
			wrappee.failed(e);
			wrappee = null;
		}
	}
	*/
}
