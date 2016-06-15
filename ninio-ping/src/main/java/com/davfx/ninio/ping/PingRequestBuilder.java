package com.davfx.ninio.ping;

public interface PingRequestBuilder {
	PingRequestBuilder receiving(PingReceiver receiver);
	PingRequest ping(String host);
}
