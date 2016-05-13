package com.davfx.ninio.proxy.v3;

import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.TcpSocket;
import com.davfx.ninio.core.v3.UdpSocket;
import com.davfx.ninio.ping.v3.PingSocket;

public interface ProxyConnectorProvider extends Disconnectable {
	TcpSocket.Builder tcp();
	UdpSocket.Builder udp();
	TcpSocket.Builder ssl();
	PingSocket.Builder ping();
	WithHeaderSocketBuilder factory();
}
