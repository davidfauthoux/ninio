package com.davfx.ninio.proxy.v3;

import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.RawSocket;
import com.davfx.ninio.core.v3.TcpSocket;
import com.davfx.ninio.core.v3.UdpSocket;
import com.davfx.ninio.http.v3.HttpSocket;
import com.davfx.ninio.http.v3.WebsocketSocket;

public interface ProxyConnectorProvider extends Disconnectable {
	TcpSocket.Builder tcp();
	UdpSocket.Builder udp();
	TcpSocket.Builder ssl();
	RawSocket.Builder raw();
	WebsocketSocket.Builder websocket();
	HttpSocket.Builder http();
	WithHeaderSocketBuilder factory();
}
