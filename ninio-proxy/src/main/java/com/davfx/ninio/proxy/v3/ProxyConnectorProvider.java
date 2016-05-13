package com.davfx.ninio.proxy.v3;

import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.TcpSocket;
import com.davfx.ninio.core.v3.UdpSocket;

public interface ProxyConnectorProvider extends Disconnectable {
	TcpSocket.Builder tcp();
	UdpSocket.Builder udp();
	TcpSocket.Builder ssl();
	WithHeaderSocketBuilder factory();
}
