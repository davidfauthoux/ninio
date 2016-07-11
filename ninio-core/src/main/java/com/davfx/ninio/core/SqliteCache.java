package com.davfx.ninio.core;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.DateUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public final class SqliteCache {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SqliteCache.class);

	public static interface Builder<T> extends ConfigurableNinioBuilder<Connector, Builder<T>> {
		Builder<T> database(File database);
		Builder<T> expiration(double expiration);
		Builder<T> using(Interpreter<T> interpreter);
		Builder<T> on(ConfigurableNinioBuilder<Connector, ?> builder);
	}

	public static <T> Builder<T> builder() {
		return new Builder<T>() {
			private ConfigurableNinioBuilder<Connector, ?> builder = UdpSocket.builder();
			
			private double expiration = 0d;
			private Interpreter<T> interpreter = null;
			private File database;
			{
				try {
					database = File.createTempFile(SqliteCache.class.getName(), null);
				} catch (IOException ioe) {
					database = null;
				}
				if (database != null) {
					database.deleteOnExit();
				}
			}
			
			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			private Buffering buffering = null;
			
			@Override
			public Builder<T> closing(Closing closing) {
				this.closing = closing;
				return this;
			}
		
			@Override
			public Builder<T> connecting(Connecting connecting) {
				this.connecting = connecting;
				return this;
			}
			
			@Override
			public Builder<T> failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public Builder<T> receiving(Receiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
			@Override
			public Builder<T> buffering(Buffering buffering) {
				this.buffering = buffering;
				return this;
			}
			
			@Override
			public Builder<T> using(Interpreter<T> interpreter) {
				this.interpreter = interpreter;
				return this;
			}
			
			@Override
			public Builder<T> on(ConfigurableNinioBuilder<Connector, ?> builder) {
				this.builder = builder;
				return this;
			}
		
			@Override
			public Builder<T> database(File database) {
				this.database = database;
				return this;
			}
			@Override
			public Builder<T> expiration(double expiration) {
				this.expiration = expiration;
				return this;
			}
			
			@Override
			public Connector create(Queue queue) {
				if (builder == null) {
					throw new NullPointerException("builder");
				}
				if (interpreter == null) {
					throw new NullPointerException("interpreter");
				}
				if (database == null) {
					throw new NullPointerException("database");
				}
				
				builder.closing(closing);
				builder.connecting(connecting);
				builder.failing(failing);
				builder.buffering(buffering);
				return new InnerConnector<>(queue, database, expiration, interpreter, receiver, builder);
			}
		};
	}
	
	private static final class InnerConnector<T> implements Connector {
		private final File databaseFile;
		private final Cache<Address, CacheByAddress<T>> cacheByDestinationAddress;
		private final Receiver r;
		private final Interpreter<T> i;
		private final Connector c;
		private final Connection sqlConnection;
		private final double expiration;
		
		public InnerConnector(Queue queue, File database, double expiration, Interpreter<T> interpreter, Receiver receiver, ConfigurableNinioBuilder<Connector, ?> builder) {
			this.expiration = expiration;
			
			databaseFile = database;
			databaseFile.delete();

			try {
				sqlConnection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getCanonicalPath());
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

			cacheByDestinationAddress = cacheMapExpiringAfterAccess(expiration);
			r = receiver;
			i = interpreter;

			builder.receiving(new Receiver() {
				@Override
				public void received(Connector conn, Address address, ByteBuffer sourceBuffer) {
					T sub = i.handleResponse(sourceBuffer.duplicate());
					if (sub == null) {
						LOGGER.trace("Invalid response (address = {})", address);
						return;
					}
	
					String key;
					List<T> to;
					synchronized (cacheByDestinationAddress) {
						CacheByAddress<T> cache = cacheByDestinationAddress.getIfPresent(address);
						if (cache == null) {
							LOGGER.trace("No cache (address = {})", address);
							return;
						}
						
						key = cache.subToKey.getIfPresent(sub);
						if (key == null) {
							LOGGER.trace("No key (address = {}, sub = {}) - {}", address, sub, cache.subToKey.asMap());
							return;
						}
	
						cache.subToKey.invalidate(sub);
	
						Cache<T, Double> subs = cache.requestsByKey.getIfPresent(key);
						if (subs == null) {
							LOGGER.trace("No corresponding subs (address = {}, sub = {}, key = {})", address, sub, key);
							return;
						}
						
						to = new LinkedList<>(subs.asMap().keySet());
						subs.invalidateAll();
					}

					for (T s : to) {
						ByteBuffer b = i.transform(sourceBuffer.duplicate(), s);
						if (b != null) {
							if (r != null) {
								r.received(InnerConnector.this, address, b);
							}
						}
					}

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
			});
			
			c = builder.create(queue);
		}

		@Override
		public void close() {
			c.close();
			try {
				sqlConnection.close();
			} catch (SQLException e) {
			}
			databaseFile.delete();
		}
		
		@Override
		public Connector send(Address address, ByteBuffer sourceBuffer) {
			double now = DateUtils.now();
			
			Context<T> context = i.handleRequest(sourceBuffer.duplicate());
			if (context == null) {
				LOGGER.trace("Invalid request (address = {})", address);
				return this;
			}

			boolean send;
			synchronized (cacheByDestinationAddress) {
				CacheByAddress<T> cache = cacheByDestinationAddress.getIfPresent(address);
				if (cache == null) {
					LOGGER.trace("New cache (address = {}, expiration = {})", address, expiration);
					cache = new CacheByAddress<T>(expiration);
					cacheByDestinationAddress.put(address, cache);
				}
	
				Cache<T, Double> subs = cache.requestsByKey.getIfPresent(context.key);
				if (subs == null) {
					subs = cacheMapExpiringAfterWrite(expiration);
					cache.requestsByKey.put(context.key, subs);
	
					subs.put(context.sub, now);
					cache.subToKey.put(context.sub, context.key);

					send = true;
					LOGGER.trace("New request (address = {}, key = {}, sub = {}) - {}", address, context.key, context.sub, cache.subToKey.asMap());
				} else {
					subs.put(context.sub, now);
					cache.subToKey.put(context.sub, context.key);

					send = false;
					LOGGER.trace("Request already sent (address = {}, key = {}, sub = {}) - {}", address, context.key, context.sub, cache.subToKey.asMap());
				}
			}

			if (send) {
				c.send(address, sourceBuffer);
			} else {
				try {
					try (PreparedStatement s = sqlConnection.prepareStatement("SELECT `packet` FROM `data` WHERE `address`= ? AND `key` = ?")) {
						int k = 1;
						s.setString(k++, address.toString());
						s.setString(k++, context.key);
						ResultSet rs = s.executeQuery();
						while (rs.next()) {
							ByteBuffer bb = ByteBuffer.wrap(rs.getBytes("packet"));
							
							LOGGER.trace("Response exists (address = {}, key = {}, sub = {})", address, context.key, context.sub);
							
							if (r != null) {
								r.received(this, address, i.transform(bb, context.sub));
							}
							return this;
						}
					}
				} catch (SQLException se) {
					LOGGER.error("SQL error", se);
				}

				LOGGER.error("Response does not exist (address = {}, key = {}, sub = {})", address, context.key, context.sub);
			}
			
			return this;
		}
	}
	
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
			requestsByKey = cacheMapExpiringAfterAccess(expiration);
			subToKey = cacheMapExpiringAfterWrite(expiration);
		}
	}

	private static <T, U> Cache<T, U> cacheMapExpiringAfterAccess(double expiration) {
		return ((expiration == 0d) ? CacheBuilder.newBuilder() : CacheBuilder.newBuilder().expireAfterAccess((long) (expiration * 1000d), TimeUnit.MILLISECONDS)).build();
	}
	private static <T, U> Cache<T, U> cacheMapExpiringAfterWrite(double expiration) {
		return ((expiration == 0d) ? CacheBuilder.newBuilder() : CacheBuilder.newBuilder().expireAfterWrite((long) (expiration * 1000d), TimeUnit.MILLISECONDS)).build();
	}
}
