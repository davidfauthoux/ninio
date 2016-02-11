package com.davfx.ninio.snmp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
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
public final class InternalSnmpMemoryCacheServerReadyFactory implements ReadyFactory, Closeable, AutoCloseable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(InternalSnmpMemoryCacheServerReadyFactory.class);

	private static final Config CONFIG = ConfigFactory.load(InternalSnmpMemoryCacheServerReadyFactory.class.getClassLoader());

	private static final double CACHE_EXPIRATION = ConfigUtils.getDuration(CONFIG, "ninio.snmp.server.cache.expiration");
	private static final double REQUEST_TIMEOUT = ConfigUtils.getDuration(CONFIG, "ninio.snmp.server.cache.timeout");
	private static final double CHECK_TIME = ConfigUtils.getDuration(CONFIG, "ninio.snmp.server.cache.check");
	private static final double REPEAT_TIME = ConfigUtils.getDuration(CONFIG, "ninio.snmp.server.cache.repeat");

	private static final String NO_COMMUNITY = "xxx";
	
	public static interface Filter {
		boolean cache(Address address, Oid oid);
	}

	private final Queue queue;
	private final ReadyFactory wrappee;
	
	private final Filter filter;
	
	private static final class RequestKey {
		public final int request;
		public final Oid oid;
		public RequestKey(int request, Oid oid) {
			this.request = request;
			this.oid = oid;
		}
		@Override
		public int hashCode() {
			return Objects.hash(oid, request);
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (!(obj instanceof RequestKey)) {
				return false;
			}
			RequestKey other = (RequestKey) obj;
			return (request == other.request) && oid.equals(other.oid);
		}
	}
	
	private static final class Cache {
		public final List<OidElement> data = new LinkedList<>();
		public final Map<Integer, Requesting> requestingByRequestId = new HashMap<>();
		public final Map<RequestKey, Double> requestingByRequestKey = new HashMap<>();
		public Cache() {
		}
	}
	
	private final Map<Address, Cache> cacheByAddress = new HashMap<>();

	private final Closeable closeable;
	
	public InternalSnmpMemoryCacheServerReadyFactory(Filter filter, Queue queue, ReadyFactory wrappee) {
		this.queue = queue;
		this.wrappee = wrappee;
		this.filter = filter;
		
		closeable = QueueScheduled.schedule(queue, CHECK_TIME, new Runnable() {
			@Override
			public void run() {
				LOGGER.trace("Checking {}", CHECK_TIME);
				double now = DateUtils.now();
				
				Iterator<Map.Entry<Address, Cache>> i = cacheByAddress.entrySet().iterator();
				while (i.hasNext()) {
					Map.Entry<Address, Cache> e = i.next();
					Address address = e.getKey();
					Cache cache = e.getValue();
					
					Iterator<OidElement> dataIterator = cache.data.iterator();
					OidElement previous = null;
					while (dataIterator.hasNext()) {
						OidElement d = dataIterator.next();
						if (d.expired(now)) {
							LOGGER.trace("Expired: {}/{}", address, d.oid);
							if (previous != null) {
								previous.lastOfBulk = true;
							}
							dataIterator.remove();
						} else {
							previous = d;
						}
					}
					
					boolean shouldSolve = false;
					Iterator<Map.Entry<RequestKey, Double>> requestingIterator = cache.requestingByRequestKey.entrySet().iterator();
					while (requestingIterator.hasNext()) {
						Map.Entry<RequestKey, Double> d = requestingIterator.next();
						RequestKey requestKey = d.getKey();
						Double requestTimestamp = d.getValue();
						
						if ((now - requestTimestamp) > REQUEST_TIMEOUT) {
							LOGGER.trace("Timeout: {}/{}", address, requestKey.oid);
							if (requestKey.request == BerConstants.GET) {
								set(cache, requestKey.oid, BerConstants.ERROR_STATUS_TIMEOUT, 0, 0, 0, null);
							} else if (requestKey.request == BerConstants.GETNEXT) {
								set(cache, requestKey.oid, 0, 0, BerConstants.ERROR_STATUS_TIMEOUT, 0, null);
							} else if (requestKey.request == BerConstants.GETBULK) {
								set(cache, requestKey.oid, 0, 0, BerConstants.ERROR_STATUS_TIMEOUT, 0, null);
							} else {
								LOGGER.error("Invalid request: {}", requestKey.request);
							}
							requestingIterator.remove();
							shouldSolve = true;
						}
					}
					
					if (shouldSolve) {
						Iterator<Map.Entry<Integer, Requesting>> k = cache.requestingByRequestId.entrySet().iterator();
						while (k.hasNext()) {
							Map.Entry<Integer, Requesting> ee = k.next();
							int ii = ee.getKey();
							Requesting rr = ee.getValue();
							if (solve(address, cache, ii, rr)) {
								i.remove();
							}
						}
					}
					
					if (cache.requestingByRequestKey.isEmpty() && cache.requestingByRequestId.isEmpty() && cache.data.isEmpty()) {
						LOGGER.trace("Cache removed: {}", address);
						i.remove();
					}
				}
			}
		});
	}
	
	@Override
	public void close() {
		closeable.close();
	}
	
	private static final class Requesting {
		public final int request;
		public final int bulkLength;
		public final Oid oid;
		public final ReadyConnection connection;
		
		public Requesting(int request, int bulkLength, Oid oid, ReadyConnection connection) {
			this.request = request;
			this.bulkLength = bulkLength;
			this.oid = oid;
			this.connection = connection;
		}
	}
	
	private static final class OidElement {
		public final Oid oid;
		public int errorStatus;
		public int errorIndex;
		public int nextErrorStatus;
		public int nextErrorIndex;
		public String value;
		public boolean lastOfBulk;

		private final double timestamp;

		public OidElement(double timestamp, Oid oid, int errorStatus, int errorIndex, int nextErrorStatus, int nextErrorIndex, String value, boolean lastOfBulk) {
			this.timestamp = timestamp;
			this.oid = oid;
			this.errorStatus = errorStatus;
			this.errorIndex = errorIndex;
			this.nextErrorStatus = nextErrorStatus;
			this.nextErrorIndex = nextErrorIndex;
			this.value = value;
			this.lastOfBulk = lastOfBulk;
		}

		public boolean expired(double now) {
			return (now - timestamp) > CACHE_EXPIRATION;
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
				
				Cache c = cacheByAddress.get(address);
				if (c == null) {
					c = new Cache();
					cacheByAddress.put(address, c);
				}
				final Cache cache = c;
				
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
								int bulkLength;
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
											bulkLength = ber.readInteger();
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
								
								Requesting r = new Requesting(request, bulkLength, oid, connection);
								
								if (solve(address, cache, requestId, r)) {
									return;
								}

								cache.requestingByRequestId.put(requestId, r);

								RequestKey requestKey = new RequestKey(request, oid);
								
								Double requestTimestamp = cache.requestingByRequestKey.get(requestKey);
								if (requestTimestamp != null) {
									if ((now - requestTimestamp) <= REPEAT_TIME) {
										LOGGER.trace("Not repeating: {}", oid);
										return;
									}
								}
								
								cache.requestingByRequestKey.put(requestKey, now);

								LOGGER.trace("Actually calling for: {}", oid);
								write.handle(address, sourceBuffer);
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
						
						if (errorStatus == 0) {
							// We scan and remove opaque values
							for (Result r : results) {
								if (r.getValue() == null) {
									errorStatus = BerConstants.NO_SUCH_NAME_ERROR;
									errorIndex = 0;
									results = null;
									break;
								}
							}
						}
						
						LOGGER.trace("Response for {}: {}:{}/{} ({})", requestId, errorStatus, errorIndex, results, address);
						
						Requesting r = cache.requestingByRequestId.get(requestId);
						if (r == null) {
							LOGGER.trace("Invalid request: {}", requestId, results);
							return;
						}

						cache.requestingByRequestKey.remove(new RequestKey(r.request, r.oid));
					
						if (errorStatus != 0) {
							if (r.request == BerConstants.GET) {
								set(cache, r.oid, errorStatus, errorIndex, 0, 0, null);
							} else if (r.request == BerConstants.GETNEXT) {
								set(cache, r.oid, 0, 0, errorStatus, errorIndex, null);
							} else if (r.request == BerConstants.GETBULK) {
								set(cache, r.oid, 0, 0, errorStatus, errorIndex, null);
							} else {
								LOGGER.error("Invalid request: {}", r.request);
							}
						} else {
							set(cache, r.oid, 0, 0, 0, 0, results);
						}
						
						Iterator<Map.Entry<Integer, Requesting>> i = cache.requestingByRequestId.entrySet().iterator();
						while (i.hasNext()) {
							Map.Entry<Integer, Requesting> e = i.next();
							int ii = e.getKey();
							Requesting rr = e.getValue();
							if (solve(address, cache, ii, rr)) {
								i.remove();
							}
						}
					}
					
					@Override
					public void close() {
						connection.close();
					}
				});
			}
		});
	}
	
	private void set(Cache cache, Oid oid, int errorStatus, int errorIndex, int nextErrorStatus, int nextErrorIndex, Iterable<Result> results) {
		double now = DateUtils.now();
		
		if (results == null) {
			int cacheIndex = 0;
			OidElement previous = null;
			OidElement toReplace = null;
			while (cacheIndex < cache.data.size()) {
				OidElement e = cache.data.get(cacheIndex);
				int c = e.oid.compareTo(oid);
				if (c == 0) {
					toReplace = e;
				}
				if (c >= 0) {
					break;
				}
				cacheIndex++;
				previous = e;
			}
			
			if (errorStatus != 0) {
				if (previous != null) {
					if (!previous.lastOfBulk) {
						previous.nextErrorStatus = 0;
						previous.nextErrorIndex = 0;
					}
				}
			}
			
			if (toReplace == null) {
				LOGGER.trace("Set error in cache: {} = {}/{}", oid, errorStatus, nextErrorStatus);
				cache.data.add(cacheIndex, new OidElement(now, internOid(oid), errorStatus, errorIndex, nextErrorStatus, nextErrorIndex, null, true));
			} else {
				LOGGER.trace("Replace error in cache: {} = {}/{}", oid, errorStatus, nextErrorStatus);
				if (errorStatus != 0) {
					toReplace.errorStatus = errorStatus;
					toReplace.errorIndex = errorIndex;
					toReplace.value = null;
				}
				if (nextErrorStatus != 0) {
					toReplace.nextErrorStatus = nextErrorStatus;
					toReplace.nextErrorIndex = nextErrorIndex;
				}
			}
		} else {
			Iterator<Result> responseIterator = results.iterator();
			int cacheIndex = 0;
			OidElement previous = null;
			while (responseIterator.hasNext()) {
				Result result = responseIterator.next();

				OidElement toReplace = null;
				while (cacheIndex < cache.data.size()) {
					OidElement e = cache.data.get(cacheIndex);
					int c = e.oid.compareTo(result.getOid());
					if (c == 0) {
						toReplace = e;
					}
					if (c >= 0) {
						break;
					}
					cacheIndex++;
					previous = e;
				}

				if (previous != null) {
					if (!previous.lastOfBulk) {
						previous.nextErrorStatus = 0;
						previous.nextErrorIndex = 0;
					}
				}
				
				if (toReplace == null) {
					LOGGER.trace("Set value in cache: {} = {}", result.getOid(), result.getValue());
					cache.data.add(cacheIndex, new OidElement(now, internOid(result.getOid()), 0, 0, 0, 0, internString(result.getValue()), !responseIterator.hasNext()));
				} else {
					LOGGER.trace("Replace value in cache: {} = {}/{}", result.getOid(), result.getValue());
					toReplace.errorStatus = 0;
					toReplace.errorIndex = 0;
					toReplace.value = result.getValue();
					if (responseIterator.hasNext()) {
						toReplace.lastOfBulk = false;
					}
				}
			}
		}
	}
	private static boolean solve(Address address, Cache cache, int requestId, Requesting r) {
		if (r.request == BerConstants.GET) {
			LOGGER.trace("Want to solve for GET {} ({})", r.oid, requestId);
			for (OidElement e : cache.data) {
				int c = e.oid.compareTo(r.oid);
				if (c == 0) {
					if (e.errorStatus != 0) {
						LOGGER.trace("Error for GET {}: {} ({})", r.oid, e.errorStatus, requestId);
						ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, e.errorStatus, e.errorIndex, null);
						r.connection.handle(address, builtBuffer);
					} else {
						LOGGER.trace("Solved for GET {}: {} ({})", r.oid, e.oid, requestId);
						ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, 0, 0, Arrays.asList(new Result(e.oid, e.value)));
						r.connection.handle(address, builtBuffer);
					}
					return true;
				}
			}
		} else if (r.request == BerConstants.GETNEXT) {
			LOGGER.trace("Want to solve for GETNEXT {} ({})", r.oid, requestId);
			boolean found = false;
			for (OidElement e : cache.data) {
				if (found) {
					LOGGER.trace("Solved for GETNEXT {}: {} ({})", r.oid, e.oid, requestId);
					if (e.errorStatus != 0) {
						ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, e.errorStatus, e.errorIndex, null);
						r.connection.handle(address, builtBuffer);
					} else {
						ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, 0, 0, Arrays.asList(new Result(e.oid, e.value)));
						r.connection.handle(address, builtBuffer);
					}
					return true;
				}
				int c = e.oid.compareTo(r.oid);
				if (c == 0) {
					if (e.nextErrorStatus != 0) {
						LOGGER.trace("Error for GETNEXT {}: {} = {} ({})", r.oid, e.oid, e.nextErrorStatus, requestId);
						ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, e.nextErrorStatus, e.nextErrorIndex, null);
						r.connection.handle(address, builtBuffer);
						return true;
					}
					found = true;
				}
			}
		} else if (r.request == BerConstants.GETBULK) {
			LOGGER.trace("Want to solve for GETBULK {} ({})", r.oid, requestId);
			boolean found = false;
			List<Result> rr = new LinkedList<>();
			int errorStatus = 0;
			int errorIndex = 0;
			for (OidElement e : cache.data) {
				if (found) {
					LOGGER.trace("Solved for GETBULK {}: {} ({})", r.oid, e.oid, requestId);
					if (e.errorStatus != 0) {
						errorStatus = e.errorStatus;
						errorIndex = e.errorIndex;
						break;
					}
					rr.add(new Result(e.oid, e.value));
					if (e.lastOfBulk || (rr.size() == r.bulkLength)) {
						break;
					}
				}
				int c = e.oid.compareTo(r.oid);
				if (c == 0) {
					if (e.nextErrorStatus != 0) {
						LOGGER.trace("Error for GETBULK {}: {} = {} ({})", r.oid, e.oid, e.nextErrorStatus, requestId);
						ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, e.nextErrorStatus, e.nextErrorIndex, null);
						r.connection.handle(address, builtBuffer);
						return true;
					}
					found = true;
				}
			}
			if (!rr.isEmpty()) {
				ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, 0, 0, rr);
				r.connection.handle(address, builtBuffer);
				return true;
			}
			if (errorStatus != 0) {
				ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, errorStatus, errorIndex, null);
				r.connection.handle(address, builtBuffer);
				return true;
			}
		}
		return false;
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
	
	// Memory saving!
	private final Map<Oid, Oid> internOids = new HashMap<>();
	// private final Map<String, String> internStrings = new HashMap<>();
	
	private Oid internOid(Oid oid) {
		Oid intern = internOids.get(oid);
		if (intern == null) {
			intern = oid;
			internOids.put(intern, intern);
		}
		return intern;
	}
	private String internString(String s) {
		// We do not intern-cache strings
		/*
		String intern = internStrings.get(s);
		if (intern == null) {
			intern = s;
			internStrings.put(intern, intern);
		}
		return intern;
		*/
		return s;
	}
	
}
