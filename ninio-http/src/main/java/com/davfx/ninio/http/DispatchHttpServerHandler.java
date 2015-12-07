package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.google.common.base.Function;

public final class DispatchHttpServerHandler implements HttpServerHandler {
	private final Function<HttpRequest, HttpServerHandler> dispatchMap;
	private HttpServerHandler currentHandler = null;

	public DispatchHttpServerHandler(Function<HttpRequest, HttpServerHandler> dispatchMap) {
		this.dispatchMap = dispatchMap;
	}
	
	@Override
	public void handle(HttpRequest request) {
		currentHandler = dispatchMap.apply(request);
		
		if (currentHandler == null) {
			currentHandler = new HttpServerHandler() {
				@Override
				public void failed(IOException e) {
				}
				@Override
				public void close() {
				}
				@Override
				public void handle(Address address, ByteBuffer buffer) {
				}
				@Override
				public void ready(Write write) {
					write.write(new HttpResponse(HttpStatus.NOT_FOUND, HttpMessage.NOT_FOUND));
					write.close();
					// write.failed(new IOException("No handler provided"));
				}
				@Override
				public void handle(HttpRequest request) {
				}
			};
		}
		
		currentHandler.handle(request);
	}
	
	@Override
	public void handle(Address address, ByteBuffer buffer) {
		currentHandler.handle(address, buffer);
	}
	
	@Override
	public void close() {
		if (currentHandler != null) {
			currentHandler.close();
		}
		currentHandler = null;
	}
	
	@Override
	public void failed(IOException e) {
		if (currentHandler != null) {
			currentHandler.failed(e);
		}
		currentHandler = null;
	}
	
	@Override
	public void ready(Write write) {
		currentHandler.ready(write);
	}
}
