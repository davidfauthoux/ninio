package com.davfx.ninio.http.util;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.http.Http;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpServerHandler;

public final class HttpServerHandlerToSimpleHttpServerHandler implements HttpServerHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerHandlerToSimpleHttpServerHandler.class);
	
	private final SimpleHttpServerHandler handler;
	private final String contentType;
	private HttpRequest request = null;
	private InMemoryPost post = null;
	
	public HttpServerHandlerToSimpleHttpServerHandler(SimpleHttpServerHandler handler) {
		this(Http.ContentType.TEXT, handler);
	}
	public HttpServerHandlerToSimpleHttpServerHandler(String contentType, SimpleHttpServerHandler handler) {
		this.contentType = contentType;
		this.handler = handler;
	}
	
	@Override
	public void handle(HttpRequest request) {
		this.request = request;
	}

	@Override
	public void handle(Address address, ByteBuffer buffer) {
		if (post == null) {
			post = new InMemoryPost();
		}
		post.add(buffer);
	}
	
	@Override
	public void ready(Write write) {
		HttpQuery query = new HttpQuery(request.getPath());
		
		String path = query.getPath();
		Parameters parameters = query.getParameters();
		
		String response;
		switch (request.getMethod()) {
		case GET:
			response = handler.get(path, parameters);
			break;
		case DELETE:
			response = handler.delete(path, parameters);
			break;
		case HEAD:
			response = handler.head(path, parameters);
			break;
		case PUT:
			response = handler.put(path, parameters);
			break;
		case POST:
			response = handler.post(path, parameters, post);
			break;
		default:
			response = null;
			break;
		}
		HttpResponse r = new HttpResponse(Http.Status.OK, Http.Message.OK);
		if (contentType != null) {
			r.getHeaders().put(Http.CONTENT_TYPE, contentType);
		}
		ByteBuffer bb;
		if (response != null) {
			bb = ByteBuffer.wrap(response.getBytes(Http.UTF8_CHARSET));
			r.getHeaders().put(Http.CONTENT_LENGTH, String.valueOf(bb.remaining()));
		} else {
			bb = null;
		}
		write.write(r);
		if (bb != null) {
			write.handle(null, bb);
		}
		write.close();
		request = null;
		post = null;
	}
	
	@Override
	public void failed(IOException e) {
		LOGGER.debug("Client connection failed", e);
	}
	
	@Override
	public void close() {
		LOGGER.debug("Connection closed by peer");
	}
}
