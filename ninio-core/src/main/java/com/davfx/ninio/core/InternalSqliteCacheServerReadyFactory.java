package com.davfx.ninio.core;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public final class InternalSqliteCacheServerReadyFactory<T> implements ReadyFactory, Closeable, AutoCloseable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(InternalSqliteCacheServerReadyFactory.class);

	private final double expiration;
	
	public static final class Context<T> {
		public final String key;
		public final T sub;
		public Context(String key, T sub) {
			this.key = key;
			this.sub = sub;
		}
	}
	public interface Interpreter<T> {
		Context<T> handleRequest(ByteBuffer packet);
		T handleResponse(ByteBuffer packet);
		ByteBuffer transform(ByteBuffer packet, T sub);
	}
	
	private static final class CacheByAddress<T> {
		public final Cache<String, Cache<T, Double>> requestsByKey;
		public final Cache<T, String> subToKey;
		public CacheByAddress(double expiration) {
			requestsByKey = CacheBuilder.newBuilder().expireAfterAccess((long) (expiration * 1000d), TimeUnit.MILLISECONDS).build();
			subToKey = CacheBuilder.newBuilder().expireAfterWrite((long) (expiration * 1000d), TimeUnit.MILLISECONDS).build();
		}
	}
	
	private final Queue queue;
	
	private final File database;
	private final ReadyFactory wrappee;

	private final Connection sqlConnection;
	
	private final Interpreter<T> interpreter;
	
	private final Cache<Address, CacheByAddress<T>> cacheByDestinationAddress;
	
	public InternalSqliteCacheServerReadyFactory(double expiration, Interpreter<T> interpreter, File database, Queue queue, ReadyFactory wrappee) {
		this.expiration = expiration;
		this.queue = queue;
		this.wrappee = wrappee;

		cacheByDestinationAddress = CacheBuilder.newBuilder().expireAfterAccess((long) (expiration * 1000d), TimeUnit.MILLISECONDS).build();

		this.interpreter = interpreter;
		
		this.database = database;
		database.delete();
		
		try {
			sqlConnection = DriverManager.getConnection("jdbc:sqlite:" + database.getCanonicalPath());
			try {
				try (PreparedStatement s = sqlConnection.prepareStatement("CREATE TABLE `data` (`address` TEXT, `key` TEXT, `packet` BLOB, PRIMARY KEY (`address`, `key`))")) {
					s.executeUpdate();
				}
			} catch (SQLException see) {
				sqlConnection.close();
				throw see;
			}
		} catch (SQLException | IOException e) {
			throw new RuntimeException("Could not connect to sqlite database", e);
		}
	}
	
	@Override
	public void close() {
		database.delete();
	}
	
	private static double now() {
		return System.currentTimeMillis() / 1000d;
	}
	
	@Override
	public Ready create() {
		final Ready wrappeeReady = wrappee.create();
		LOGGER.trace("Ready created");
		return new QueueReady(queue, new Ready() {
			@Override
			public void connect(Address bindAddress, final ReadyConnection connection) {
				LOGGER.trace("Connecting {}", bindAddress);
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
								double now = now();
								
								Context<T> context = interpreter.handleRequest(sourceBuffer.duplicate());
								if (context == null) {
									LOGGER.trace("Invalid request (address = {})", address);
									return;
								}

								CacheByAddress<T> cache = cacheByDestinationAddress.getIfPresent(address);
								if (cache == null) {
									LOGGER.trace("New cache (address = {}, expiration = {})", address, expiration);
									cache = new CacheByAddress<T>(expiration);
									cacheByDestinationAddress.put(address, cache);
								}

								Cache<T, Double> subs = cache.requestsByKey.getIfPresent(context.key);
								if (subs == null) {
									subs = CacheBuilder.newBuilder().expireAfterWrite((long) (expiration * 1000d), TimeUnit.MILLISECONDS).build();
									cache.requestsByKey.put(context.key, subs);

									subs.put(context.sub, now);
									cache.subToKey.put(context.sub, context.key);

									LOGGER.trace("New request (address = {}, key = {}, sub = {})", address, context.key, context.sub);
									
									write.handle(address, sourceBuffer);
								} else {
									LOGGER.trace("Request already sent (address = {}, key = {}, sub = {})", address, context.key, context.sub);
									
									subs.put(context.sub, now);
									cache.subToKey.put(context.sub, context.key);

									try {
										try (PreparedStatement s = sqlConnection.prepareStatement("SELECT `packet` FROM `data` WHERE `address`= ? AND `key` = ?")) {
											int k = 1;
											s.setString(k++, address.toString());
											s.setString(k++, context.key);
											ResultSet rs = s.executeQuery();
											while (rs.next()) {
												ByteBuffer bb = ByteBuffer.wrap(rs.getBytes("packet"));
												
												LOGGER.trace("Response exists (address = {}, key = {}, sub = {})", address, context.key, context.sub);
												
												connection.handle(address, interpreter.transform(bb, context.sub));
												return;
											}
										}
									} catch (SQLException se) {
										LOGGER.error("SQL error", se);
									}

									LOGGER.error("Response does not exist (address = {}, key = {}, sub = {})", address, context.key, context.sub);
								}
							}
						});
					}
					
					@Override
					public void handle(Address address, ByteBuffer sourceBuffer) {
						T sub = interpreter.handleResponse(sourceBuffer.duplicate());
						if (sub == null) {
							LOGGER.trace("Invalid response (address = {})", address);
							return;
						}

						CacheByAddress<T> cache = cacheByDestinationAddress.getIfPresent(address);
						if (cache == null) {
							LOGGER.trace("No cache (address = {}, sub = {})", address, sub);
							return;
						}
						
						String key = cache.subToKey.getIfPresent(sub);
						if (key == null) {
							LOGGER.trace("No key (address = {}, sub = {})", address, sub);
							return;
						}
						
						Cache<T, Double> subs = cache.requestsByKey.getIfPresent(key);
						if (subs == null) {
							LOGGER.trace("No corresponding subs (address = {}, sub = {}, key = {})", address, sub, key);
							return;
						}
						
						for (T s : subs.asMap().keySet()) {
							ByteBuffer b = interpreter.transform(sourceBuffer.duplicate(), s);
							if (b != null) {
								connection.handle(address, b);
							}
						}
						subs.invalidateAll();
						cache.subToKey.invalidateAll();

						LOGGER.trace("New response (address = {}, sub = {}, key = {})", address, sub, key);

						try {
							byte[] bytes = new byte[sourceBuffer.remaining()];
							sourceBuffer.get(bytes);
							try (PreparedStatement s = sqlConnection.prepareStatement("REPLACE INTO `data` (`address`, `key`, `packet`) VALUES (?, ?, ?)")) {
								int k = 1;
								s.setString(k++, address.toString());
								s.setString(k++, key);
								s.setBytes(k++, bytes);
								s.executeUpdate();
							}
						} catch (SQLException se) {
							LOGGER.error("SQL error", se);
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
}
