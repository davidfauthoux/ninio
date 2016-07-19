package com.davfx.ninio.http;

public interface HttpRequestBuilder {
	HttpRequestBuilder maxRedirections(int maxRedirections);
	
	interface HttpRequestBuilderHttpContentSender extends HttpRequestBuilder, HttpContentSender {
	}
	
	HttpRequestBuilderHttpContentSender build(HttpRequest request);
	HttpContentSender receive(HttpReceiver callback);
}
