package com.davfx.ninio.http;

public interface HttpRequestBuilder {
	HttpRequestBuilder maxRedirections(int maxRedirections);
	HttpContentSender build(HttpRequest request);
}
