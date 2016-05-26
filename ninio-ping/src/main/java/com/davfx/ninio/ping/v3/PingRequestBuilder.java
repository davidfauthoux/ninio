package com.davfx.ninio.ping.v3;

public interface PingRequestBuilder {
	PingRequestBuilder receiving(PingReceiver receiver);
	PingRequest ping(String host);
}
