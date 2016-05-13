package com.savarese.rocksaw.net;

import java.net.InetAddress;

public interface PingHandler {
	void pong(InetAddress from, double time, double ttl);
}
