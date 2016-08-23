package com.davfx.ninio.ping;

import com.davfx.ninio.core.Disconnectable;

public interface PingConnecter extends Disconnectable {
	void connect(PingConnection callback);
	Cancelable ping(byte[] ip, PingReceiver callback);
}
