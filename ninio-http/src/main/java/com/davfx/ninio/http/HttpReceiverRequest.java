package com.davfx.ninio.http;

@Deprecated
public interface HttpReceiverRequest {
	HttpContentSender create(HttpRequest request);
}
