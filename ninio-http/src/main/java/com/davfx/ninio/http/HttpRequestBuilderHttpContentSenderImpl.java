package com.davfx.ninio.http;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.http.HttpRequestBuilder.HttpRequestBuilderHttpContentSender;

final class HttpRequestBuilderHttpContentSenderImpl implements HttpRequestBuilderHttpContentSender {
	private final HttpRequestBuilder that;
	private final HttpContentSender sender;
	
	public HttpRequestBuilderHttpContentSenderImpl(HttpRequestBuilder that, HttpContentSender sender) {
		this.that = that;
		this.sender = sender;
	}

	@Override
	public HttpContentSender send(ByteBuffer buffer, SendCallback callback) {
		sender.send(buffer, callback);
		return this;
	}
	@Override
	public void finish() {
		sender.finish();
	}
	@Override
	public void cancel() {
		sender.cancel();
	}
	
	@Override
	public HttpContentSender receive(HttpReceiver callback) {
		return that.receive(callback);
	}
	
	@Override
	public HttpRequestBuilder maxRedirections(int maxRedirections) {
		return that.maxRedirections(maxRedirections);
	}
	
	@Override
	public HttpRequestBuilderHttpContentSender build(HttpRequest request) {
		return that.build(request);
	}

}
