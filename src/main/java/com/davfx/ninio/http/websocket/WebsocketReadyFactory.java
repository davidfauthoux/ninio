package com.davfx.ninio.http.websocket;

import com.davfx.ninio.common.ByteBufferAllocator;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.http.HttpClient;

public final class WebsocketReadyFactory implements ReadyFactory {
	private final HttpClient httpClient;

	public WebsocketReadyFactory(HttpClient httpClient) {
		this.httpClient = httpClient;
	}
	
	@Override
	public Ready create(Queue queue, ByteBufferAllocator allocator) {
		return new WebsocketReady(httpClient);
	}
}
