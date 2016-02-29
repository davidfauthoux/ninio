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
		public final Address address;
		public final int request;
		public final Oid oid;
		public RequestKey(Address address, int request, Oid oid) {
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
			if (!(obj instanceof RequestKey)) {
				return false;
			}
			RequestKey other = (RequestKey) obj;
			return address.equals(other.address) && (request == other.request) && oid.equals(other.oid);
		}
	}
	
	private static final class Cache {
		public final Map<Integer, Requesting> requestingByRequestId = new HashMap<>();
		public final Map<RequestKey, Double> requestingByRequestKey = new HashMap<>();
		public Cache() {
		}
	}
	
	private final Cache cache = new Cache();

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
				try (PreparedStatement s = sqlConnection.prepareStatement("CREATE TABLE `data` (`address` TEXT, `oid` TEXT, `timestamp` DOUBLE, `errorStatus` INT, `errorIndex` INT, `value` TEXT, `firstOfBulk` BOOLEAN, `lastOfBulk` BOOLEAN, PRIMARY KEY (`address`, `oid`))")) {
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
				
				boolean shouldSolve = false;
				Iterator<Map.Entry<RequestKey, Double>> requestingIterator = cache.requestingByRequestKey.entrySet().iterator();
				while (requestingIterator.hasNext()) {
					Map.Entry<RequestKey, Double> d = requestingIterator.next();
					RequestKey requestKey = d.getKey();
					Double requestTimestamp = d.getValue();
					
					if ((now - requestTimestamp) > REQUEST_TIMEOUT) {
						LOGGER.trace("Timeout: {}/{}", requestKey.address, requestKey.oid);
						setError(cache, requestKey.address, requestKey.oid, requestKey.request, BerConstants.ERROR_STATUS_TIMEOUT, 0);
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
						if (solve(rr.address, cache, ii, rr)) {
							k.remove();
						}
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
		public final Address address;
		public final int request;
		public final int bulkLength;
		public final Oid oid;
		public final ReadyConnection connection;
		
		public Requesting(Address address, int request, int bulkLength, Oid oid, ReadyConnection connection) {
			this.address = address;
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
			public void connect(Address bindAddress, final ReadyConnection connection) {
				if (bindAddress != null) {
					LOGGER.error("Invalid bind address (should be null): {}", bindAddress);
					connection.failed(new IOException("Invalid bind address (should be null): " + bindAddress));
					return;
				}
				
				wrappeeReady.connect(bindAddress, new ReadyConnection() {
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
								
								/*%%
								if (false) {
									Requesting r = new Requesting(request, bulkLength, oid, connection);
									cache.requestingByRequestId.put(requestId, r);
									write.handle(address, sourceBuffer);
									return;
								}
								*/
								
								double now = DateUtils.now();
								
								Requesting r = new Requesting(address, request, bulkLength, oid, connection);
								
								if (solve(address, cache, requestId, r)) {
									return;
								}

								cache.requestingByRequestId.put(requestId, r);

								RequestKey requestKey = new RequestKey(address, request, oid);
								
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
					public void handle(Address address, ByteBuffer buffer) {
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
							List<Result> filteredResults = new LinkedList<>();
							for (Result r : results) {
								if (r.getValue() != null) {
									filteredResults.add(r);
								}
							}
							results = filteredResults;
						}
						if (!results.iterator().hasNext()) {
							errorStatus = BerConstants.NO_SUCH_NAME_ERROR;
							errorIndex = 0;
							results = null;
						}
						
						LOGGER.trace("Response for {}: {}:{}/{} ({})", requestId, errorStatus, errorIndex, results, address);
						
						Requesting r = cache.requestingByRequestId.get(requestId);
						if (r == null) {
							LOGGER.trace("Invalid request ID: {}", requestId, results);
							return;
						}
						
						/*%%%
						if (false) {
							ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, errorStatus, errorIndex, results);
							r.connection.handle(address, builtBuffer);
							return;
						}
						*/

						cache.requestingByRequestKey.remove(new RequestKey(r.address, r.request, r.oid));
					
						if (errorStatus == 0) {
							setResults(cache, address, r.oid, r.request, results);
						} else {
							if (r.request == BerConstants.GET) {
								setError(cache, address, r.oid, r.request, errorStatus, errorIndex);
							} else if ((r.request == BerConstants.GETNEXT) || (r.request == BerConstants.GETBULK)) {
								setNextError(cache, address, r.oid, r.request, errorStatus, errorIndex);
							} else {
								LOGGER.error("Invalid request code: {}", r.request);
							}
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
	
	private void setError(Cache cache, Address address, Oid oid, int request, int errorStatus, int errorIndex) {
		double now = DateUtils.now();
		
		LOGGER.trace("Set GET error in cache: oid = {}, errorStatus = {}", oid, errorStatus);
		try {
			try (PreparedStatement s = sqlConnection.prepareStatement("REPLACE INTO `data` (`address`, `oid`, `timestamp`, `errorStatus`, `errorIndex`, `value`, `firstOfBulk`, `lastOfBulk`) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
				int k = 1;
				s.setString(k++, address.toString());
				s.setString(k++, oid.toString());
				s.setDouble(k++, now);
				s.setInt(k++, errorStatus);
				s.setInt(k++, errorIndex);
				s.setString(k++, null);
				s.setBoolean(k++, true);
				s.setBoolean(k++, true);
				s.executeUpdate();
			}
		} catch (SQLException se) {
			LOGGER.error("SQL error", se);
		}
	}
	
	private void setNextError(Cache cache, Address address, Oid oid, int request, int errorStatus, int errorIndex) {
		double now = DateUtils.now();
		
		LOGGER.trace("Set next error in cache: oid = {}, errorStatus = {}", oid, errorStatus);
		try {
			try (PreparedStatement s = sqlConnection.prepareStatement("REPLACE INTO `next` (`address`, `oid`, `timestamp`, `nextErrorStatus`, `nextErrorIndex`) VALUES (?, ?, ?, ?, ?)")) {
				int k = 1;
				s.setString(k++, address.toString());
				s.setString(k++, oid.toString());
				s.setDouble(k++, now);
				s.setInt(k++, errorStatus);
				s.setInt(k++, errorIndex);
				s.executeUpdate();
			}
		} catch (SQLException se) {
			LOGGER.error("SQL error", se);
		}
	}
	
	private void setResults(Cache cache, Address address, Oid oid, int request, Iterable<Result> results) {
		double now = DateUtils.now();
		
		try {
			Oid last = null;
			try (PreparedStatement s = sqlConnection.prepareStatement("REPLACE INTO `data` (`address`, `oid`, `timestamp`, `errorStatus`, `errorIndex`, `value`, `firstOfBulk`, `lastOfBulk`) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
				{
					Iterator<Result> responseIterator = results.iterator();
					if (!responseIterator.hasNext() || !responseIterator.next().getOid().equals(oid)) {
						LOGGER.trace("Not last in cache: oid = {}", oid);
						int k = 1;
						s.setString(k++, address.toString());
						s.setString(k++, oid.toString());
						s.setDouble(k++, now);
						s.setInt(k++, 0);
						s.setInt(k++, 0);
						s.setString(k++, null);
						s.setBoolean(k++, true);
						s.setBoolean(k++, !responseIterator.hasNext());
						s.addBatch();
					}
				}
				{
					Iterator<Result> responseIterator = results.iterator();
					while (responseIterator.hasNext()) {
						Result result = responseIterator.next();
						last = result.getOid();
						LOGGER.trace("Set value in cache: {} = {}", result.getOid(), result.getValue());
						int k = 1;
						s.setString(k++, address.toString());
						s.setString(k++, result.getOid().toString());
						s.setDouble(k++, now);
						s.setInt(k++, 0);
						s.setInt(k++, 0);
						s.setString(k++, result.getValue());
						s.setBoolean(k++, false);
						s.setBoolean(k++, !responseIterator.hasNext());
						s.addBatch();
					}
				}
				s.executeBatch();
			}
			if (last != null) {
				try (PreparedStatement s = sqlConnection.prepareStatement("DELETE FROM `next` WHERE `address` = ? AND `oid` >= ? AND `oid` < ?")) {
					int k = 1;
					s.setString(k++, address.toString());
					s.setString(k++, oid.toString());
					s.setString(k++, last.toString());
					s.executeUpdate();
				}
			}
		} catch (SQLException se) {
			LOGGER.error("SQL error", se);
		}
	}

	private boolean solveGet(Address address, Cache cache, int requestId, Requesting r) {
		double now = DateUtils.now();
		double expiredTimestamp = now - CACHE_EXPIRATION;
		
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
					} else if (value == null) {
						LOGGER.trace("Error for GET {}: {} ({})", r.oid, errorStatus, requestId);
						ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, BerConstants.NO_SUCH_NAME_ERROR, 0, null);
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
	}
	
	private boolean solveGetNext(Address address, Cache cache, int requestId, Requesting r) {
		double now = DateUtils.now();
		double expiredTimestamp = now - CACHE_EXPIRATION;
		
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
			try (PreparedStatement s = sqlConnection.prepareStatement("SELECT `oid`, `value`, `firstOfBulk`, `lastOfBulk` FROM `data` WHERE `address`= ? AND `oid` >= ? AND `timestamp` >= ? ORDER BY `oid`")) {
				int k = 1;
				s.setString(k++, address.toString());
				s.setString(k++, r.oid.toString());
				s.setDouble(k++, expiredTimestamp);
				ResultSet rs = s.executeQuery();
				boolean first = true;
				while (rs.next()) {
					Oid oid = new Oid(rs.getString("oid"));
					String value = rs.getString("value");
					boolean firstOfBulk = rs.getBoolean("firstOfBulk");
					boolean lastOfBulk = rs.getBoolean("lastOfBulk");

					try {
						if (first) {
							if (oid.equals(r.oid)) {
								/*%%% if (errorStatus != 0) {
									LOGGER.trace("Error for GETNEXT {}: {} ({})", r.oid, errorStatus, requestId);
									ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, errorStatus, errorIndex, null);
									r.connection.handle(address, builtBuffer);
									return true;
								}*/
								if (lastOfBulk) {
									return false;
								}
								continue;
							} else if (firstOfBulk) {
								return false;
							}
						}
						
						if (firstOfBulk) {
							return false;
						}
						
						if (value == null) {
							if (lastOfBulk) {
								return false;
							}
							continue;
						}

						LOGGER.trace("Solved for GETNEXT {}: {} ({})", r.oid, oid, requestId);
						ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, 0, 0, Arrays.asList(new Result(oid, value)));
						r.connection.handle(address, builtBuffer);
						return true;
						
					} finally {
						first = false;
					}
				}
				return false;
			}
		} catch (SQLException se) {
			LOGGER.error("SQL error", se);
			return false;
		}
	}
	
	private boolean solveGetBulk(Address address, Cache cache, int requestId, Requesting r) {
		double now = DateUtils.now();
		double expiredTimestamp = now - CACHE_EXPIRATION;
		
		LOGGER.trace("Want to solve for GETBULK {} ({})", r.oid, requestId);
		
		List<Result> rr = new LinkedList<>();
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
			try (PreparedStatement s = sqlConnection.prepareStatement("SELECT `oid`, `value`, `firstOfBulk`, `lastOfBulk` FROM `data` WHERE `address`= ? AND `oid` >= ? AND `timestamp` >= ? ORDER BY `oid`")) {
				int k = 1;
				s.setString(k++, address.toString());
				s.setString(k++, r.oid.toString());
				s.setDouble(k++, expiredTimestamp);
				ResultSet rs = s.executeQuery();
				boolean first = true;
				while (rs.next()) {
					Oid oid = new Oid(rs.getString("oid"));
					String value = rs.getString("value");
					boolean firstOfBulk = rs.getBoolean("firstOfBulk");
					boolean lastOfBulk = rs.getBoolean("lastOfBulk");

					try {
						if (first) {
							if (oid.equals(r.oid)) {
								/*%%%% if (errorStatus != 0) {
									LOGGER.trace("Error for GETBULK {}: {} ({})", r.oid, errorStatus, requestId);
									ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, errorStatus, errorIndex, null);
									r.connection.handle(address, builtBuffer);
									return true;
								}
								*/
								if (lastOfBulk) {
									return false;
								}
								continue;
							} else if (firstOfBulk) {
								return false;
							}
						}
						
						if (firstOfBulk) {
							break;
						}
						
						if (value == null) {
							if (lastOfBulk) {
								break;
							}
							continue;
						}

						LOGGER.trace("Solved for GETBULK {}: {} ({})", r.oid, oid, requestId);
						rr.add(new Result(oid, value));

						if (lastOfBulk || (rr.size() == r.bulkLength)) {
							break;
						}

					} finally {
						first = false;
					}
				}
			}
		} catch (SQLException se) {
			LOGGER.error("SQL error", se);
			return false;
		}

		LOGGER.trace("Solved for GETBULK {}: size = {} ({})", r.oid, rr.size(), requestId);
		ByteBuffer builtBuffer = build(requestId, NO_COMMUNITY, 0, 0, rr);
		r.connection.handle(address, builtBuffer);
		return true;
	}
	
	private boolean solve(Address address, Cache cache, int requestId, Requesting r) {
		if (r.request == BerConstants.GET) {
			return solveGet(address, cache, requestId, r);
		} else if (r.request == BerConstants.GETNEXT) {
			return solveGetNext(address, cache, requestId, r);
		} else if (r.request == BerConstants.GETBULK) {
			return solveGetBulk(address, cache, requestId, r);
		} else {
			LOGGER.error("Invalid request code: {}", r.request);
			return false;
		}
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
