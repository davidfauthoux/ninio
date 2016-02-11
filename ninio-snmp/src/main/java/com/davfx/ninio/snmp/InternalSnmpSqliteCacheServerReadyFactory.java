package com.davfx.ninio.snmp;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
public final class InternalSnmpSqliteCacheServerReadyFactory implements ReadyFactory, Closeable, AutoCloseable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(InternalSnmpSqliteCacheServerReadyFactory.class);

	private static final Config CONFIG = ConfigFactory.load(InternalSnmpSqliteCacheServerReadyFactory.class.getClassLoader());

	private static final double CACHE_EXPIRATION = ConfigUtils.getDuration(CONFIG, "ninio.snmp.server.cache.expiration");
	private static final double REQUEST_TIMEOUT = ConfigUtils.getDuration(CONFIG, "ninio.snmp.server.cache.timeout");
	private static final double CHECK_TIME = ConfigUtils.getDuration(CONFIG, "ninio.snmp.server.cache.check");
	private static final double REPEAT_TIME = ConfigUtils.getDuration(CONFIG, "ninio.snmp.server.cache.repeat");

	private static final String NO_COMMUNITY = "xxx";
	
	private final Queue queue;
	private final ReadyFactory wrappee;
	
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
		public final Map<Integer, Requesting> requestingByRequestId = new HashMap<>();
		public final Map<RequestKey, Double> requestingByRequestKey = new HashMap<>();
		public Cache() {
		}
	}
	
	private final Map<Address, Cache> cacheByAddress = new HashMap<>();

	private final Closeable closeable;
	private final Connection sqlConnection;
	
	public InternalSnmpSqliteCacheServerReadyFactory(File database, Queue queue, ReadyFactory wrappee) {
		this.queue = queue;
		this.wrappee = wrappee;
		
		try {
			sqlConnection = DriverManager.getConnection("jdbc:sqlite:" + database.getCanonicalPath());
			try {
				try (PreparedStatement s = sqlConnection.prepareStatement("DROP TABLE IF EXISTS `data`")) {
					s.executeUpdate();
				}
				try (PreparedStatement s = sqlConnection.prepareStatement("DROP TABLE IF EXISTS `next`")) {
					s.executeUpdate();
				}
				try (PreparedStatement s = sqlConnection.prepareStatement("CREATE TABLE `data` (`address` TEXT, `oid` TEXT, `timestamp` DOUBLE, `errorStatus` INT, `errorIndex` INT, `value` TEXT, `lastOfBulk` BOOLEAN, PRIMARY KEY (`address`, `oid`))")) {
					s.executeUpdate();
				}
				try (PreparedStatement s = sqlConnection.prepareStatement("CREATE TABLE `next` (`address` TEXT, `oid` TEXT, `timestamp` DOUBLE, `nextErrorStatus` INT, `nextErrorIndex` INT, PRIMARY KEY (`address`, `oid`))")) {
					s.executeUpdate();
				}
			} catch (SQLException see) {
				sqlConnection.close();
				throw see;
			}
		} catch (SQLException | IOException e) {
			throw new RuntimeException("Could not connect to sqlite database", e);
		}
		
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
					
					boolean shouldSolve = false;
					Iterator<Map.Entry<RequestKey, Double>> requestingIterator = cache.requestingByRequestKey.entrySet().iterator();
					while (requestingIterator.hasNext()) {
						Map.Entry<RequestKey, Double> d = requestingIterator.next();
						RequestKey requestKey = d.getKey();
						Double requestTimestamp = d.getValue();
						
						if ((now - requestTimestamp) > REQUEST_TIMEOUT) {
							LOGGER.trace("Timeout: {}/{}", address, requestKey.oid);
							set(cache, address, requestKey.oid, requestKey.request, BerConstants.ERROR_STATUS_TIMEOUT, 0, null);
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
					
					if (cache.requestingByRequestKey.isEmpty() && cache.requestingByRequestId.isEmpty()) {
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
	
	@Override
	public Ready create() {
		LOGGER.trace("Ready created");
		final Ready wrappeeReady = wrappee.create();
		return new QueueReady(queue, new Ready() {
			@Override
			public void connect(final Address address, final ReadyConnection connection) {
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

								LOGGER.trace("{}: Request {} with community: {} and oid: {}", address, requestId, community, oid);
								
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
										LOGGER.trace("{}: Not repeating: {}", address, oid);
										return;
									}
								}
								
								cache.requestingByRequestKey.put(requestKey, now);

								LOGGER.trace("{}: Actually calling for: {}", address, oid);
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
					
						set(cache, address, r.oid, r.request, errorStatus, errorIndex, results);
						
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
	
	private void set(Cache cache, Address address, Oid oid, int request, int errStatus, int errIndex, Iterable<Result> res) {
		double now = DateUtils.now();
		
		int errorStatus;
		int errorIndex;
		int nextErrorStatus;
		int nextErrorIndex;
		Iterable<Result> results;
		
		if (errStatus == 0) {
			errorStatus = 0;
			errorIndex = 0;
			nextErrorStatus = 0;
			nextErrorIndex = 0;
			results = res;
		} else {
			if (request == BerConstants.GET) {
				errorStatus = errStatus;
				errorIndex = errIndex;
				nextErrorStatus = 0;
				nextErrorIndex = 0;
			} else if (request == BerConstants.GETNEXT) {
				errorStatus = 0;
				errorIndex = 0;
				nextErrorStatus = errStatus;
				nextErrorIndex = errIndex;
			} else if (request == BerConstants.GETBULK) {
				errorStatus = 0;
				errorIndex = 0;
				nextErrorStatus = errStatus;
				nextErrorIndex = errIndex;
			} else {
				LOGGER.error("Invalid request: {}", request);
				return;
			}
			results = null;
		}
		
		if (results == null) {
			if (nextErrorStatus != 0) {
				LOGGER.trace("Set next error in cache: {} = {}/{}", oid, errorStatus, nextErrorStatus);
				try {
					try (PreparedStatement s = sqlConnection.prepareStatement("REPLACE INTO `next` (`address`, `oid`, `timestamp`, `nextErrorStatus`, `nextErrorIndex`) VALUES (?, ?, ?, ?, ?)")) {
						int k = 1;
						s.setString(k++, address.toString());
						s.setString(k++, oid.toString());
						s.setDouble(k++, now);
						s.setInt(k++, nextErrorStatus);
						s.setInt(k++, nextErrorIndex);
						s.executeUpdate();
					}
				} catch (SQLException se) {
					LOGGER.error("SQL error", se);
				}
			} else {
				LOGGER.trace("Set error in cache: {} = {}/{}", oid, errorStatus, nextErrorStatus);
				try {
					try (PreparedStatement s = sqlConnection.prepareStatement("REPLACE INTO `data` (`address`, `oid`, `timestamp`, `errorStatus`, `errorIndex`, `value`, `lastOfBulk`) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
						int k = 1;
						s.setString(k++, address.toString());
						s.setString(k++, oid.toString());
						s.setDouble(k++, now);
						s.setInt(k++, errorStatus);
						s.setInt(k++, errorIndex);
						s.setString(k++, null);
						s.setBoolean(k++, true);
						s.executeUpdate();
					}
				} catch (SQLException se) {
					LOGGER.error("SQL error", se);
				}
			}
		} else {
			try {
				try (PreparedStatement s = sqlConnection.prepareStatement("REPLACE INTO `data` (`address`, `oid`, `timestamp`, `errorStatus`, `errorIndex`, `value`, `lastOfBulk`) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
					Iterator<Result> responseIterator = results.iterator();
					while (responseIterator.hasNext()) {
						Result result = responseIterator.next();
						LOGGER.trace("Set value in cache: {} = {}", result.getOid(), result.getValue());
						int k = 1;
						s.setString(k++, address.toString());
						s.setString(k++, result.getOid().toString());
						s.setDouble(k++, now);
						s.setInt(k++, 0);
						s.setInt(k++, 0);
						s.setString(k++, result.getValue());
						s.setBoolean(k++, !responseIterator.hasNext());
						s.addBatch();
					}
					s.executeBatch();
				}
			} catch (SQLException se) {
				LOGGER.error("SQL error", se);
			}
		}
	}
	
	private boolean solve(Address address, Cache cache, int requestId, Requesting r) {
		double now = DateUtils.now();
		double expiredTimestamp = now - CACHE_EXPIRATION;
		
		if (r.request == BerConstants.GET) {
			LOGGER.trace("Want to solve for GET {} ({})", r.oid, requestId);
			try {
				try (PreparedStatement s = sqlConnection.prepareStatement("SELECT `oid`, `errorStatus`, `errorIndex`, `value` FROM `data` WHERE `address`= ? AND `oid` = ? AND `timestamp` >= ?")) {
					int k = 1;
					s.setString(k++, address.toString());
					s.setString(k++, r.oid.toString());
					s.setDouble(k++, expiredTimestamp);
					ResultSet rs = s.executeQuery();
					while (rs.next()) {
						Oid oid = new Oid(rs.getString("oid"));
						int errorStatus = rs.getInt("errorStatus");
						int errorIndex = rs.getInt("errorIndex");
						String value = rs.getString("value");
						if (errorStatus != 0) {
							LOGGER.trace("Error for GET {}: {} ({})", r.oid, errorStatus, requestId);
							ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, errorStatus, errorIndex, null);
							r.connection.handle(address, builtBuffer);
						} else {
							LOGGER.trace("Solved for GET {}: {} ({})", r.oid, oid, requestId);
							ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, 0, 0, Arrays.asList(new Result(oid, value)));
							r.connection.handle(address, builtBuffer);
						}
						return true;
					}
					return false;
				}
			} catch (SQLException se) {
				LOGGER.error("SQL error", se);
				return false;
			}
		} else if (r.request == BerConstants.GETNEXT) {
			LOGGER.trace("Want to solve for GETNEXT {} ({})", r.oid, requestId);

			try {
				try (PreparedStatement s = sqlConnection.prepareStatement("SELECT `oid`, `nextErrorStatus`, `nextErrorIndex` FROM `next` WHERE `address`= ? AND `oid` = ? AND `timestamp` >= ?")) {
					int k = 1;
					s.setString(k++, address.toString());
					s.setString(k++, r.oid.toString());
					s.setDouble(k++, expiredTimestamp);
					ResultSet rs = s.executeQuery();
					while (rs.next()) {
						Oid oid = new Oid(rs.getString("oid"));
						int nextErrorStatus = rs.getInt("nextErrorStatus");
						int nextErrorIndex = rs.getInt("nextErrorIndex");

						if (nextErrorStatus != 0) {
							LOGGER.trace("Error for GETNEXT {}: {} = {} ({})", r.oid, oid, nextErrorStatus, requestId);
							ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, nextErrorStatus, nextErrorIndex, null);
							r.connection.handle(address, builtBuffer);
							return true;
						}
					}
				}
				try (PreparedStatement s = sqlConnection.prepareStatement("SELECT `oid`, `errorStatus`, `errorIndex`, `value` FROM `data` WHERE `address`= ? AND `oid` > ? AND `timestamp` >= ? ORDER BY `oid`")) {
					int k = 1;
					s.setString(k++, address.toString());
					s.setString(k++, r.oid.toString());
					s.setDouble(k++, expiredTimestamp);
					ResultSet rs = s.executeQuery();
					while (rs.next()) {
						Oid oid = new Oid(rs.getString("oid"));
						int errorStatus = rs.getInt("errorStatus");
						int errorIndex = rs.getInt("errorIndex");
						String value = rs.getString("value");

						if (errorStatus != 0) {
							ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, errorStatus, errorIndex, null);
							r.connection.handle(address, builtBuffer);
							return true;
						}

						if (value == null) {
							break;
						} else {
							LOGGER.trace("Solved for GETNEXT {}: {} ({})", r.oid, oid, requestId);
							ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, 0, 0, Arrays.asList(new Result(oid, value)));
							r.connection.handle(address, builtBuffer);
							return true;
						}
					}
					return false;
				}
			} catch (SQLException se) {
				LOGGER.error("SQL error", se);
				return false;
			}
		} else if (r.request == BerConstants.GETBULK) {
			LOGGER.trace("Want to solve for GETBULK {} ({})", r.oid, requestId);
			
			List<Result> rr = new LinkedList<>();
			int globalErrorStatus = 0;
			int globalErrorIndex = 0;
			try {
				try (PreparedStatement s = sqlConnection.prepareStatement("SELECT `oid`, `nextErrorStatus`, `nextErrorIndex` FROM `next` WHERE `address`= ? AND `oid` = ? AND `timestamp` >= ?")) {
					int k = 1;
					s.setString(k++, address.toString());
					s.setString(k++, r.oid.toString());
					s.setDouble(k++, expiredTimestamp);
					ResultSet rs = s.executeQuery();
					while (rs.next()) {
						Oid oid = new Oid(rs.getString("oid"));
						int nextErrorStatus = rs.getInt("nextErrorStatus");
						int nextErrorIndex = rs.getInt("nextErrorIndex");

						if (nextErrorStatus != 0) {
							LOGGER.trace("Error for GETBULK {}: {} = {} ({})", r.oid, oid, nextErrorStatus, requestId);
							ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, nextErrorStatus, nextErrorIndex, null);
							r.connection.handle(address, builtBuffer);
							return true;
						}
					}
				}
				try (PreparedStatement s = sqlConnection.prepareStatement("SELECT `oid`, `errorStatus`, `errorIndex`, `value`, `lastOfBulk` FROM `data` WHERE `address`= ? AND `oid` > ? AND `timestamp` >= ? ORDER BY `oid`")) {
					int k = 1;
					s.setString(k++, address.toString());
					s.setString(k++, r.oid.toString());
					s.setDouble(k++, expiredTimestamp);
					ResultSet rs = s.executeQuery();
					while (rs.next()) {
						Oid oid = new Oid(rs.getString("oid"));
						int errorStatus = rs.getInt("errorStatus");
						int errorIndex = rs.getInt("errorIndex");
						String value = rs.getString("value");
						boolean lastOfBulk = rs.getBoolean("lastOfBulk");
						
						if (errorStatus != 0) {
							globalErrorStatus = errorStatus;
							globalErrorIndex = errorIndex;
							break;
						}

						if (value == null) {
							break;
						} else {
							LOGGER.trace("Solved for GETBULK {}: {} ({})", r.oid, oid, requestId);
							rr.add(new Result(oid, value));
							if (lastOfBulk || (rr.size() == r.bulkLength)) {
								break;
							}
						}
					}
				}
			} catch (SQLException se) {
				LOGGER.error("SQL error", se);
				return false;
			}

			if (!rr.isEmpty()) {
				ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, 0, 0, rr);
				r.connection.handle(address, builtBuffer);
				return true;
			}
			if (globalErrorStatus != 0) {
				ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, globalErrorStatus, globalErrorIndex, null);
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
}
