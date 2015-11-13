package com.davfx.ninio.proxy;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class ProxyClient implements AutoCloseable, Closeable {
	
	private static final Config CONFIG = ConfigFactory.load(ProxyClient.class.getClassLoader());
	
	public static final double CONNECTION_TIMEOUT = ConfigUtils.getDuration(CONFIG, "ninio.proxy.timeout.connection");
	public static final double READ_TIMEOUT = ConfigUtils.getDuration(CONFIG, "ninio.proxy.timeout.read");

	private static final double THROTTLE_BYTES_PER_SECOND = CONFIG.getDouble("ninio.proxy.throttle.bps");
	private static final double THROTTLE_TIME_STEP = ConfigUtils.getDuration(CONFIG, "ninio.proxy.throttle.step.time");
	private static final long THROTTLE_BYTES_STEP = CONFIG.getBytes("ninio.proxy.throttle.step.bytes");

	private final ProxyReadyGenerator proxyReadyGenerator;

	public ProxyClient(Address proxyServerAddress, ProxyListener listener) {
		proxyReadyGenerator = new ProxyReadyGenerator(proxyServerAddress, CONNECTION_TIMEOUT, READ_TIMEOUT, THROTTLE_BYTES_PER_SECOND, THROTTLE_TIME_STEP, THROTTLE_BYTES_STEP, listener);
	}

	@Override
	public void close() {
		proxyReadyGenerator.close();
	}
	
	public ReadyFactory socket() {
		return new ProxyReadyFactory(proxyReadyGenerator, ProxyCommons.Types.SOCKET);
	}
	public ReadyFactory datagram() {
		return new ProxyReadyFactory(proxyReadyGenerator, ProxyCommons.Types.DATAGRAM);
	}
	public ReadyFactory ping() {
		return new ProxyReadyFactory(proxyReadyGenerator, ProxyCommons.Types.PING);
	}
	public ReadyFactory hop() {
		return new ProxyReadyFactory(proxyReadyGenerator, ProxyCommons.Types.HOP);
	}
	public ReadyFactory of(String type) {
		return new ProxyReadyFactory(proxyReadyGenerator, type);
	}

	public ProxyClient override(String type, ClientSideConfigurator configurator) {
		proxyReadyGenerator.override(type, configurator);
		return this;
	}
	
	public ProxyClient hopTo(Address address, String type) {
		override(ProxyCommons.Types.HOP, new HopClientSideConfigurator(address, type));
		return this;
	}
}
