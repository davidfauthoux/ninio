package com.davfx.ninio.proxy;

import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.RawSocket;
import com.davfx.ninio.core.TcpSocket;
import com.davfx.ninio.core.UdpSocket;
import com.davfx.ninio.http.HttpSocket;
import com.davfx.ninio.http.WebsocketSocket;

public interface ProxyConnectorProvider extends Disconnectable {
	TcpSocket.Builder tcp();
	UdpSocket.Builder udp();
	TcpSocket.Builder ssl();
	RawSocket.Builder raw();
	WebsocketSocket.Builder websocket();
	HttpSocket.Builder http();
	WithHeaderSocketBuilder factory();
}
