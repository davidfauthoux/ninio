package com.davfx.ninio.ping.v3;

public interface PingReceiver {
	void received(String host, double time);
}
