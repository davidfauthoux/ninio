package com.davfx.ninio.http.v3;

public interface HttpReceiverRequest {
	HttpContentSender create(HttpRequest request);
}
