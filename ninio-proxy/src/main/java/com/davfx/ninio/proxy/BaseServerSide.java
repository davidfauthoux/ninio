package com.davfx.ninio.proxy;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.ByAddressDatagramReadyFactory;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.core.SocketReadyFactory;
import com.davfx.ninio.core.TcpdumpSyncDatagramReady;
import com.davfx.ninio.core.TcpdumpSyncDatagramReadyFactory;
import com.davfx.ninio.ping.InternalPingServerReadyFactory;
import com.davfx.ninio.ping.PureJavaSyncPing;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

public final class BaseServerSide implements ServerSide {
	private static final Logger LOGGER = LoggerFactory.getLogger(BaseServerSide.class);
	
	private static final Config CONFIG = ConfigFactory.load(BaseServerSide.class.getClassLoader());
	
	private static enum Mode {
		SYNC_TCPDUMP, ASYNC
	}
	
	private static final Mode MODE;
	private static final int TCPDUMP_PORT;
	private static final String TCPDUMP_INTERFACE;
	static {
		String datagramMode = CONFIG.getString("ninio.proxy.mode.datagram");
		if (datagramMode.equals("sync.tcpdump")) {
			MODE = Mode.SYNC_TCPDUMP;
			TCPDUMP_PORT = CONFIG.getInt("ninio.proxy.tcpdump.port");
			// boolean promiscuous = CONFIG.getBoolean("proxy.tcpdump.promiscuous");
			TCPDUMP_INTERFACE = CONFIG.getString("ninio.proxy.tcpdump.interface");
		} else if (datagramMode.equals("async")) {
			MODE = Mode.ASYNC;
			TCPDUMP_PORT = -1;
			TCPDUMP_INTERFACE = null;
		} else {
			throw new ConfigException.BadValue("ninio.proxy.mode.datagram", "Only sync.tcmpdump|async modes allowed");
		}
	}

	private final Map<String, ServerSideConfigurator> configurators = new ConcurrentHashMap<>();
	private final Closeable toClose;
	
	public BaseServerSide(Queue queue) {
		
		configurators.put(ProxyCommons.Types.SOCKET, new SimpleServerSideConfigurator(new SocketReadyFactory()));

		ReadyFactory datagramReadyFactory;
		switch (MODE) {
		case SYNC_TCPDUMP:
			TcpdumpSyncDatagramReady.Rule rule;
			if (TCPDUMP_PORT < 0) {
				rule = new TcpdumpSyncDatagramReady.EmptyRule();
			} else {
				rule = new TcpdumpSyncDatagramReady.SourcePortRule(TCPDUMP_PORT);
			}
			TcpdumpSyncDatagramReady.Receiver receiver = new TcpdumpSyncDatagramReady.Receiver(rule, TCPDUMP_INTERFACE); // Never closed, but could be when proxy server is closed
			try {
				receiver.prepare();
			} catch (IOException ioe) {
				LOGGER.error("Tcpdump receiver could not be prepared", ioe);
			}
			datagramReadyFactory = new TcpdumpSyncDatagramReadyFactory(receiver);
			toClose = receiver;
			break;
		case ASYNC:
			ByAddressDatagramReadyFactory byAddressDatagramReadyFactory = new ByAddressDatagramReadyFactory(queue); // Never closed, but could be when proxy server is closed
			datagramReadyFactory = byAddressDatagramReadyFactory;
			toClose = byAddressDatagramReadyFactory;
			break;
		default:
			throw new IllegalStateException("Mode not implemented: " + MODE);
		}

		configurators.put(ProxyCommons.Types.DATAGRAM, new SimpleServerSideConfigurator(datagramReadyFactory));

		configurators.put(ProxyCommons.Types.PING, new SimpleServerSideConfigurator(new InternalPingServerReadyFactory(new PureJavaSyncPing())));

		configurators.put(ProxyCommons.Types.HOP, new HopServerSideConfigurator(new ProxyListener() {
			@Override
			public void failed(IOException e) {
				LOGGER.warn("Hop failed", e);
			}
			@Override
			public void disconnected() {
				LOGGER.debug("Hop disconnected");
			}
			@Override
			public void connected() {
				LOGGER.debug("Hop connected");
			}
		}));
	}

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
	
	@Override
	public void close() {
		toClose.close();
	}
}
