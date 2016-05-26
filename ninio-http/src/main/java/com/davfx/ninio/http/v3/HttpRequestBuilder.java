package com.davfx.ninio.http.v3;

import com.davfx.ninio.core.v3.Failing;

public interface HttpRequestBuilder {
	HttpRequestBuilder failing(Failing failing);
	HttpRequestBuilder receiving(HttpReceiver receiver);
	HttpRequestBuilder maxRedirections(int maxRedirections);
	HttpContentSender build(HttpRequest request);
}
