package com.davfx.ninio.proxy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.DatagramReadyFactory;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.common.SocketReadyFactory;
import com.davfx.ninio.ping.InternalPingServerReadyFactory;
import com.davfx.ninio.ping.PureJavaSyncPing;
import com.davfx.ninio.proxy.sync.SyncDatagramReady;
import com.davfx.ninio.proxy.sync.SyncDatagramReadyFactory;
import com.davfx.ninio.proxy.sync.TcpdumpSyncDatagramReady;
import com.davfx.ninio.proxy.sync.TcpdumpSyncDatagramReadyFactory;
import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class ProxyUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyUtils.class);

	private static final Config CONFIG = ConfigUtils.load(ProxyUtils.class);

	public static final String SOCKET_TYPE = CONFIG.getString("proxy.socket");
	public static final String DATAGRAM_TYPE = CONFIG.getString("proxy.datagram");
	public static final String PING_TYPE = CONFIG.getString("proxy.ping");
	public static final String REPROXY_TYPE = CONFIG.getString("proxy.reproxy");
	//%% private static final int PING_MAX_SIMULTANEOUS_CLIENTS = ConfigUtils.load(InternalPingServerReadyFactory.class).getInt("ping.maxSimultaneousClients");
	
	private ProxyUtils() {
	}
	
	public static interface ClientSideConfigurator {
		void configure(String connecterType, DataOutputStream out) throws IOException;
	}
	public static interface ClientSide {
		void override(String type, ClientSideConfigurator configurator);
		void write(String connecterType, DataOutputStream out) throws IOException;
	}
	
	public static final class EmptyClientSideConfiguration implements ClientSideConfigurator {
		public EmptyClientSideConfiguration() {
		}
		@Override
		public void configure(String connecterType, DataOutputStream out) throws IOException {
		}
	}
	
	public static ClientSide client() {
		final Map<String, ClientSideConfigurator> configurators = new HashMap<>(); // ConcurrentHashMap not necessary here because write() is always called from Queue
		configurators.put(SOCKET_TYPE, new EmptyClientSideConfiguration());
		configurators.put(DATAGRAM_TYPE, new EmptyClientSideConfiguration());
		configurators.put(PING_TYPE, new EmptyClientSideConfiguration());
		configurators.put(REPROXY_TYPE, new EmptyClientSideConfiguration());
		
		return new ClientSide() {
			@Override
			public void override(String type, ClientSideConfigurator configurator) {
				configurators.put(type, configurator);
			}
			@Override
			public void write(String connecterType, DataOutputStream out) throws IOException {
				out.writeUTF(connecterType);
				ClientSideConfigurator configurator = configurators.get(connecterType);
				if (configurator == null) {
					LOGGER.error("Unknown type: {}", connecterType);
					throw new IOException("Unknown type: " + connecterType);
				}
				configurator.configure(connecterType, out);
			}
		};
	}
	
	public static interface ServerSideConfigurator {
		ReadyFactory configure(String connecterType, DataInputStream in) throws IOException;
	}
	public static interface ServerSide {
		void override(String connecterType, ServerSideConfigurator configurator);
		ReadyFactory read(DataInputStream in) throws IOException;
	}
	
	public static ServerSide server() {
		final Map<String, ServerSideConfigurator> configurators = new ConcurrentHashMap<>();
		configurators.put(SOCKET_TYPE, new ServerSideConfigurator() {
			@Override
			public ReadyFactory configure(String connecterType, DataInputStream in) throws IOException {
				return new SocketReadyFactory();
			}
		});

		if (CONFIG.getBoolean("proxy.sync")) {
			if (CONFIG.hasPath("proxy.tcpdump")) {
				final TcpdumpSyncDatagramReady.Receiver syncDatagramReceiver = new TcpdumpSyncDatagramReady.Receiver(CONFIG.getString("proxy.tcpdump.interface"), CONFIG.getInt("proxy.tcpdump.port"));
				configurators.put(DATAGRAM_TYPE, new ServerSideConfigurator() {
					@Override
					public ReadyFactory configure(String connecterType, DataInputStream in) throws IOException {
						return new TcpdumpSyncDatagramReadyFactory(syncDatagramReceiver);
					}
				});
			} else {
				final SyncDatagramReady.Receiver syncDatagramReceiver = new SyncDatagramReady.Receiver();
				configurators.put(DATAGRAM_TYPE, new ServerSideConfigurator() {
					@Override
					public ReadyFactory configure(String connecterType, DataInputStream in) throws IOException {
						return new SyncDatagramReadyFactory(syncDatagramReceiver);
					}
				});
			}
		} else {
			configurators.put(DATAGRAM_TYPE, new ServerSideConfigurator() {
				@Override
				public ReadyFactory configure(String connecterType, DataInputStream in) throws IOException {
					return new DatagramReadyFactory();
				}
			});
		}
		
		configurators.put(PING_TYPE, new ServerSideConfigurator() {
			@Override
			public ReadyFactory configure(String connecterType, DataInputStream in) throws IOException {
				//%% return new InternalPingServerReadyFactory(PING_MAX_SIMULTANEOUS_CLIENTS, new PureJavaSyncPing());
				return new InternalPingServerReadyFactory(new PureJavaSyncPing());
			}
		});

		configurators.put(REPROXY_TYPE, Reproxy.server());

		return new ServerSide() {
			@Override
			public void override(String connecterType, ServerSideConfigurator configurator) {
				configurators.put(connecterType, configurator);
			}
			@Override
			public ReadyFactory read(DataInputStream in) throws IOException {
				String connecterType = in.readUTF();
				ServerSideConfigurator configurator = configurators.get(connecterType);
				if (configurator == null) {
					LOGGER.error("Unknown type: {}", connecterType);
					throw new IOException("Unknown type: " + connecterType);
				}
				return configurator.configure(connecterType, in);
			}
		};
	}
}
