package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.MemoryCache;

public final class InMemoryCache {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryCache.class);

	public static interface Builder<T> extends NinioBuilder<Connecter> {
		// Builder<T> database(File database);
		Builder<T> expiration(double expiration);
		Builder<T> using(Interpreter<T> interpreter);
		Builder<T> with(NinioBuilder<Connecter> builder);
	}

	public static <T> Builder<T> builder() {
		return new Builder<T>() {
			private NinioBuilder<Connecter> builder = UdpSocket.builder();
			
			private double expiration = 0d;
			private Interpreter<T> interpreter = null;
			// private File database = null;
			
			@Override
			public Builder<T> using(Interpreter<T> interpreter) {
				this.interpreter = interpreter;
				return this;
			}
			
			@Override
			public Builder<T> with(NinioBuilder<Connecter> builder) {
				this.builder = builder;
				return this;
			}
		
			/*
			@Override
			public Builder<T> database(File database) {
				this.database = database;
				return this;
			}
			*/
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
				
				return new InnerConnecter<>(/*database, */expiration, interpreter, builder.create(queue));
			}
		};
	}
	
	private static final class InnerConnecter<T> implements Connecter {
		// private final File database;
		private final Connecter wrappee;
		private final Interpreter<T> interpreter;
		private final double expiration;
		// private final java.sql.Connection sqlConnection;
		private final MemoryCache<Address, CacheByAddress<T>> cacheByDestinationAddress;
		private Connection connectCallback = null;

		public InnerConnecter(/*File database, */double expiration, Interpreter<T> interpreter, Connecter wrappee) {
			this.expiration = expiration;
			this.interpreter = interpreter;
			this.wrappee = wrappee;
			
			cacheByDestinationAddress = MemoryCache.<Address, CacheByAddress<T>> builder().expireAfterAccess(expiration).build();
			
			/*
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
			*/
		}
		
		@Override
		public void connect(final Connection callback) {
			/*
			if (database == null) {
				callback.failed(new IOException("Database file could not be created"));
				return;
			}
			if (sqlConnection == null) {
				callback.failed(new IOException("SQL connection could not be open"));
				return;
			}
			*/
			
			synchronized (cacheByDestinationAddress) {
				connectCallback = callback;
			}

			wrappee.connect(new Connection() {
				@Override
				public void received(Address address, ByteBuffer sourceBuffer) {
					ByteBuffer sb = sourceBuffer.duplicate();
					T sub;
					try {
						sub = interpreter.handleResponse(sb);
					} catch (Exception e) {
						LOGGER.trace("Invalid response packet", e);
						return;
					}
					if (sub == null) {
						LOGGER.trace("Invalid response (address = {})", address);
						return;
					}
	
					String key;
					List<T> to;
					synchronized (cacheByDestinationAddress) {
						CacheByAddress<T> cache = cacheByDestinationAddress.get(address);
						if (cache == null) {
							LOGGER.trace("No cache (address = {})", address);
							return;
						}
						
						key = cache.subToKey.get(sub);
						if (key == null) {
							LOGGER.trace("No key (address = {}, sub = {}) - {}", address, sub, cache.subToKey);
							return;
						}
	
						cache.subToKey.remove(sub);
	
						DataCache<T> subs = cache.requestsByKey.get(key);
						if (subs == null) {
							LOGGER.trace("No corresponding subs (address = {}, sub = {}, key = {})", address, sub, key);
							return;
						}
						
						to = new LinkedList<>();
						for (T k : subs.subs.keys()) {
							to.add(k);
						}
						subs.subs.clear();
						
						subs.data = sourceBuffer.duplicate();
					}

					for (T s : to) {
						ByteBuffer ssb = sourceBuffer.duplicate();
						ByteBuffer b;
						try {
							b = interpreter.transform(ssb, s);
						} catch (Exception e) {
							LOGGER.trace("Invalid response packet", e);
							continue;
						}
						if (b != null) {
							callback.received(address, b);
						}
					}

					LOGGER.trace("New response (address = {}, sub = {}, key = {})", address, sub, key);
	
					/*
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
					*/
				}
				
				@Override
				public void connected(Address address) {
					callback.connected(address);
				}
				
				@Override
				public void failed(IOException ioe) {
					/*
					try {
						sqlConnection.close();
					} catch (SQLException e) {
					}
					database.delete();
					*/

					callback.failed(ioe);
				}
				
				@Override
				public void closed() {
					/*
					try {
						sqlConnection.close();
					} catch (SQLException e) {
					}
					database.delete();
					*/

					callback.closed();
				}
			});
		}
		
		@Override
		public void send(Address address, ByteBuffer sourceBuffer, SendCallback sendCallback) {
			/*
			if ((database == null) || (sqlConnection == null)) {
				sendCallback.failed(new IOException("Could not be created"));
				return;
			}
			*/

			ByteBuffer sb = sourceBuffer.duplicate();
			Context<T> context;
			try {
				context = interpreter.handleRequest(sb);
			} catch (Exception e) {
				sendCallback.failed(new IOException("Invalid packet", e));
				return;
			}
			if (context == null) {
				sendCallback.failed(new IOException("Invalid request: " + address));
				return;
			}

			boolean send;
			Connection callback;
			ByteBuffer data;
			synchronized (cacheByDestinationAddress) {
				callback = connectCallback;
				
				CacheByAddress<T> cache = cacheByDestinationAddress.get(address);
				if (cache == null) {
					LOGGER.trace("New cache (address = {}, expiration = {})", address, expiration);
					cache = new CacheByAddress<T>(expiration);
					cacheByDestinationAddress.put(address, cache);
				}
	
				DataCache<T> subs = cache.requestsByKey.get(context.key);
				if (subs == null) {
					subs = new DataCache<T>(expiration);
					cache.requestsByKey.put(context.key, subs);
	
					subs.subs.put(context.sub, null);
					cache.subToKey.put(context.sub, context.key);

					send = true;
					LOGGER.trace("New request (address = {}, key = {}, sub = {}) - {}", address, context.key, context.sub, cache.subToKey);
				} else {
					subs.subs.put(context.sub, null);
					cache.subToKey.put(context.sub, context.key);

					send = false;
					LOGGER.trace("Request already sent (address = {}, key = {}, sub = {}) - {}", address, context.key, context.sub, cache.subToKey);
				}
				
				data = subs.data;
			}

			if (send) {
				wrappee.send(address, sourceBuffer, sendCallback);
			} else {
				if (callback != null) {
					if (data != null) {
						ByteBuffer b = data.duplicate();
						ByteBuffer tb;
						try {
							tb = interpreter.transform(b, context.sub);
						} catch (Exception e) {
							sendCallback.failed(new IOException("Invalid packet", e));
							return;
						}
						sendCallback.sent();
						callback.received(address, tb);
						return;
					}
					/*
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
					*/
				}

				LOGGER.trace("Response does not exist yet (address = {}, key = {}, sub = {})", address, context.key, context.sub);
				sendCallback.sent();
			}
			
		}
		
		@Override
		public void close() {
			wrappee.close();
			/*
			if (sqlConnection != null) {
				try {
					sqlConnection.close();
				} catch (SQLException e) {
				}
			}
			if (database != null) {
				database.delete();
			}
			*/
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
	
	private static final class DataCache<T> {
		public ByteBuffer data = null;
		public final MemoryCache<T, Void> subs;
		public DataCache(double expiration) {
			subs = MemoryCache.<T, Void> builder().expireAfterWrite(expiration).build();
		}
	}
	private static final class CacheByAddress<T> {
		public final MemoryCache<String, DataCache<T>> requestsByKey;
		public final MemoryCache<T, String> subToKey;
		public CacheByAddress(double expiration) {
			requestsByKey = MemoryCache.<String, DataCache<T>> builder().expireAfterAccess(expiration).build();
			subToKey = MemoryCache.<T, String> builder().expireAfterWrite(expiration).build();
		}
	}
}
