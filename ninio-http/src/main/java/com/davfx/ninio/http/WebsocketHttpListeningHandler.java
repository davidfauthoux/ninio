package com.davfx.ninio.http;

import java.io.IOException;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Listening;

public final class WebsocketHttpListeningHandler implements HttpListeningHandler {
	private final Listening listening;
	private final boolean textResponses;

	public WebsocketHttpListeningHandler(boolean textResponses, Listening listening) {
		this.textResponses = textResponses;
		this.listening = listening;
	}

	@Override
	public HttpContentReceiver handle(HttpRequest request, HttpResponseSender responseSender) {
		return new WebsocketHttpContentReceiver(request, responseSender, textResponses, listening);
	}
	
	@Override
	public void closed() {
		listening.closed();
	}
	
	@Override
	public void connected(Address address) {
		listening.connected(address);
	}
	
	@Override
	public void failed(IOException e) {
		listening.failed(e);
	}
}
