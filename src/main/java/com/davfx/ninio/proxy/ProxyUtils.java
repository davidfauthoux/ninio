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

public final class ProxyUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyUtils.class);

	public static final String SOCKET_TYPE = ProxyUtils.class.getPackage().getName() + ".socket";
	public static final String DATAGRAM_TYPE = ProxyUtils.class.getPackage().getName() + ".datagram";
	
	private ProxyUtils() {
	}
	
	public static interface ClientSideConfigurator {
		void configure(String connecterType, DataOutputStream out) throws IOException;
	}
	public static interface ClientSide {
		void override(String type, ClientSideConfigurator configurator);
		void write(String connecterType, DataOutputStream out) throws IOException;
	}
	
	public static ClientSide client() {
		final Map<String, ClientSideConfigurator> configurators = new HashMap<>(); // ConcurrentHashMap not necessary here because write() is always called from Queue
		configurators.put(SOCKET_TYPE, new ClientSideConfigurator() {
			@Override
			public void configure(String connecterType, DataOutputStream out) throws IOException {
			}
		});
		configurators.put(DATAGRAM_TYPE, new ClientSideConfigurator() {
			@Override
			public void configure(String connecterType, DataOutputStream out) throws IOException {
			}
		});
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
		configurators.put(DATAGRAM_TYPE, new ServerSideConfigurator() {
			@Override
			public ReadyFactory configure(String connecterType, DataInputStream in) throws IOException {
				return new DatagramReadyFactory();
			}
		});
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
