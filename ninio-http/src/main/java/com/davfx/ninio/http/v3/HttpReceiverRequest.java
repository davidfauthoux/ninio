package com.davfx.ninio.http.v3;

@Deprecated
public interface HttpReceiverRequest {
	HttpContentSender create(HttpRequest request);
}
