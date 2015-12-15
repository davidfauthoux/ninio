package com.davfx.ninio.http.websocket;

import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.http.HttpClient;

public final class WebsocketReadyFactory implements ReadyFactory {
	private final HttpClient httpClient;

	public WebsocketReadyFactory(HttpClient httpClient) {
		this.httpClient = httpClient;
	}
	
	@Override
	public Ready create() {
		return new WebsocketReady(httpClient);
	}
}
