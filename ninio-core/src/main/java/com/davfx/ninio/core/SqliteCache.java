package com.davfx.ninio.core;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
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

	public static interface Builder<T> extends NinioBuilder<Connecter> {
		Builder<T> database(File database);
		Builder<T> expiration(double expiration);
		Builder<T> using(Interpreter<T> interpreter);
		Builder<T> on(NinioBuilder<Connecter> builder);
	}

	public static <T> Builder<T> builder() {
		return new Builder<T>() {
			private NinioBuilder<Connecter> builder = UdpSocket.builder();
			
			private double expiration = 0d;
			private Interpreter<T> interpreter = null;
			private File database = null;
			
			@Override
			public Builder<T> using(Interpreter<T> interpreter) {
				this.interpreter = interpreter;
				return this;
			}
			
			@Override
			public Builder<T> on(NinioBuilder<Connecter> builder) {
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
			public Connecter create(Queue queue) {
				if (builder == null) {
					throw new NullPointerException("builder");
				}
				if (interpreter == null) {
					throw new NullPointerException("interpreter");
				}
				
				return new InnerConnecter<>(database, expiration, interpreter, builder.create(queue));
			}
		};
	}
	
	private static final class InnerConnecter<T> implements Connecter {
		private final File database;
		private final Connecter wrappee;
		private final Interpreter<T> interpreter;
		private final double expiration;
		private final java.sql.Connection sqlConnection;
		private final Cache<Address, CacheByAddress<T>> cacheByDestinationAddress;
		private Connection connectCallback = null;

		public InnerConnecter(File database, double expiration, Interpreter<T> interpreter, Connecter wrappee) {
			this.expiration = expiration;
			this.interpreter = interpreter;
			this.wrappee = wrappee;
			
			cacheByDestinationAddress = cacheMapExpiringAfterAccess(expiration);
			
			File databaseFile;
			if (database == null) {
				try {
					databaseFile = File.createTempFile(SqliteCache.class.getName(), null);
					databaseFile.deleteOnExit();
				} catch (IOException ioe) {
					LOGGER.error("Database file error", ioe);
					databaseFile = null;
				}
			} else {
				databaseFile = database;
			}
			databaseFile.delete();
			this.database = databaseFile;

			java.sql.Connection c;
			try {
				c = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getCanonicalPath());
				try {
					try (PreparedStatement s = c.prepareStatement("CREATE TABLE `data` (`address` TEXT, `key` TEXT, `packet` BLOB, PRIMARY KEY (`address`, `key`))")) {
						s.executeUpdate();
					}
				} catch (SQLException see) {
					c.close();
					throw see;
				}
			} catch (SQLException | IOException e) {
				LOGGER.error("SQL error", e);
				c = null;
			}
			sqlConnection = c;
		}
		
		@Override
		public void connect(final Connection callback) {
			if (database == null) {
				callback.failed(new IOException("Database file could not be created"));
				return;
			}
			if (sqlConnection == null) {
				callback.failed(new IOException("SQL connection could not be open"));
				return;
			}
			
			synchronized (cacheByDestinationAddress) {
				connectCallback = callback;
			}

			wrappee.connect(new Connection() {
				@Override
				public void received(Address address, ByteBuffer sourceBuffer) {
					T sub = interpreter.handleResponse(sourceBuffer.duplicate());
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
						ByteBuffer b = interpreter.transform(sourceBuffer.duplicate(), s);
						if (b != null) {
							callback.received(address, b);
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
				
				@Override
				public void connected(Address address) {
					callback.connected(address);
				}
				
				@Override
				public void failed(IOException ioe) {
					try {
						sqlConnection.close();
					} catch (SQLException e) {
					}
					database.delete();

					callback.failed(ioe);
				}
				
				@Override
				public void closed() {
					try {
						sqlConnection.close();
					} catch (SQLException e) {
					}
					database.delete();

					callback.closed();
				}
			});
		}
		
		@Override
		public void send(Address address, ByteBuffer sourceBuffer, SendCallback sendCallback) {
			if ((database == null) || (sqlConnection == null)) {
				sendCallback.failed(new IOException("Could not be created"));
				return;
			}

			double now = DateUtils.now();
			
			Context<T> context = interpreter.handleRequest(sourceBuffer.duplicate());
			if (context == null) {
				sendCallback.failed(new IOException("Invalid request: " + address));
				return;
			}

			boolean send;
			Connection callback;
			synchronized (cacheByDestinationAddress) {
				callback = connectCallback;
				
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
				wrappee.send(address, sourceBuffer, sendCallback);
			} else {
				sendCallback.sent();
				if (callback != null) {
					try {
						try (PreparedStatement s = sqlConnection.prepareStatement("SELECT `packet` FROM `data` WHERE `address`= ? AND `key` = ?")) {
							int k = 1;
							s.setString(k++, address.toString());
							s.setString(k++, context.key);
							ResultSet rs = s.executeQuery();
							while (rs.next()) {
								ByteBuffer bb = ByteBuffer.wrap(rs.getBytes("packet"));
								
								LOGGER.trace("Response exists (address = {}, key = {}, sub = {})", address, context.key, context.sub);
								
								callback.received(address, interpreter.transform(bb, context.sub));
								return;
							}
						}
					} catch (SQLException se) {
						LOGGER.error("SQL error", se);
					}
				}

				LOGGER.error("Response does not exist (address = {}, key = {}, sub = {})", address, context.key, context.sub);
			}
			
		}
		
		@Override
		public void close() {
			wrappee.close();
			if (sqlConnection != null) {
				try {
					sqlConnection.close();
				} catch (SQLException e) {
				}
			}
			if (database != null) {
				database.delete();
			}
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
