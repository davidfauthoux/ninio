package com.davfx.ninio.proxy;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

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
import com.davfx.util.ConfigUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class InternalSqliteCacheServerReadyFactory<T> implements ReadyFactory, Closeable, AutoCloseable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(InternalSqliteCacheServerReadyFactory.class);

	private static final Config CONFIG = ConfigFactory.load(InternalSqliteCacheServerReadyFactory.class.getClassLoader());

	private static final double CACHE_EXPIRATION = ConfigUtils.getDuration(CONFIG, "ninio.proxy.cache.expiration");
	
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
	
	private final Queue queue;
	
	private final File database;
	private final ReadyFactory wrappee;

	private final Connection sqlConnection;
	
	private final Interpreter<T> interpreter;
	
	public InternalSqliteCacheServerReadyFactory(Interpreter<T> interpreter, File database, Queue queue, ReadyFactory wrappee) {
		this.queue = queue;
		this.wrappee = wrappee;
		
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
	
	private static final class CacheByAddress<T> {
		public final Cache<String, Cache<T, Double>> requestsByKey = CacheBuilder.newBuilder().expireAfterAccess((long) (CACHE_EXPIRATION * 1000d), TimeUnit.MILLISECONDS).build();
		public final Cache<T, String> subToKey = CacheBuilder.newBuilder().expireAfterWrite((long) (CACHE_EXPIRATION * 1000d), TimeUnit.MILLISECONDS).build();
	}
	
	@Override
	public Ready create() {
		final Ready wrappeeReady = wrappee.create();
		LOGGER.trace("Ready created");
		return new QueueReady(queue, new Ready() {
			@Override
			public void connect(Address bindAddress, final ReadyConnection connection) {
				if (bindAddress != null) {
					LOGGER.error("Invalid bind address (should be null): {}", bindAddress);
					connection.failed(new IOException("Invalid bind address (should be null): " + bindAddress));
					return;
				}
				
				wrappeeReady.connect(bindAddress, new ReadyConnection() {
					private final Cache<Address, CacheByAddress<T>> cacheByDestinationAddress = CacheBuilder.newBuilder().expireAfterAccess((long) (CACHE_EXPIRATION * 1000d), TimeUnit.MILLISECONDS).build();
					
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
									return;
								}

								CacheByAddress<T> cache = cacheByDestinationAddress.getIfPresent(address);
								if (cache == null) {
									cache = new CacheByAddress<T>();
									cacheByDestinationAddress.put(address, cache);
								}

								Cache<T, Double> subs = cache.requestsByKey.getIfPresent(context.key);
								if (subs == null) {
									subs = CacheBuilder.newBuilder().expireAfterWrite((long) (CACHE_EXPIRATION * 1000d), TimeUnit.MILLISECONDS).build();
									cache.requestsByKey.put(context.key, subs);

									subs.put(context.sub, now);
									cache.subToKey.put(context.sub, context.key);

									write.handle(address, sourceBuffer);
								} else {
									subs.put(context.sub, now);
									cache.subToKey.put(context.sub, context.key);

									try {
										try (PreparedStatement s = sqlConnection.prepareStatement("SELECT `packet` FROM `data` WHERE `address`= ? AND `key` = ?")) {
											int k = 1;
											s.setString(k++, address.toString());
											s.setString(k++, context.key);
											ResultSet rs = s.executeQuery();
											while (rs.next()) {
												Blob packet = rs.getBlob("packet");
												ByteBuffer bb = ByteBuffer.wrap(packet.getBytes(1L, (int) packet.length()));
												
												connection.handle(address, interpreter.transform(bb, context.sub));
											}
										}
									} catch (SQLException se) {
										LOGGER.error("SQL error", se);
									}
								}
							}
						});
					}
					
					@Override
					public void handle(Address address, ByteBuffer sourceBuffer) {
						T sub = interpreter.handleResponse(sourceBuffer.duplicate());
						if (sub == null) {
							return;
						}

						CacheByAddress<T> cache = cacheByDestinationAddress.getIfPresent(address);
						if (cache == null) {
							return;
						}
						
						String key = cache.subToKey.getIfPresent(sub);
						if (key == null) {
							return;
						}
						
						Cache<T, Double> subs = cache.requestsByKey.getIfPresent(key);
						if (subs == null) {
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

						try {
							Blob blob = sqlConnection.createBlob();
							blob.setBytes(1L, sourceBuffer.array(), sourceBuffer.arrayOffset() + sourceBuffer.position(), sourceBuffer.remaining());
							try (PreparedStatement s = sqlConnection.prepareStatement("REPLACE INTO `data` (`address`, `key`, `packet`) VALUES (?, ?, ?)")) {
								int k = 1;
								s.setString(k++, address.toString());
								s.setString(k++, key);
								s.setBlob(k++, blob);
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
