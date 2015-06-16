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
import com.google.gson.JsonElement;

public final class HttpServerHandlerToJsonHttpServerHandler implements HttpServerHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerHandlerToJsonHttpServerHandler.class);
	
	private final JsonHttpServerHandler handler;
	private HttpRequest request = null;
	private InMemoryPost post = null;
	
	public HttpServerHandlerToJsonHttpServerHandler(JsonHttpServerHandler handler) {
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
	public void ready(final Write write) {
		HttpQuery query = new HttpQuery(request.getPath());
		
		String path = query.getPath();
		Parameters parameters = query.getParameters();
		
		JsonHttpServerHandler.Callback callback = new JsonHttpServerHandler.Callback() {
			@Override
			public void send(JsonElement response) {
				if (response == null) {
					HttpResponse r = new HttpResponse(Http.Status.INTERNAL_SERVER_ERROR, Http.Message.INTERNAL_SERVER_ERROR);
					write.write(r);
					write.close();
					return;
				} else {
					HttpResponse r = new HttpResponse(Http.Status.OK, Http.Message.OK);
					r.getHeaders().put(Http.CONTENT_TYPE, Http.ContentType.JSON);
					ByteBuffer bb = ByteBuffer.wrap(response.toString().getBytes(Http.UTF8_CHARSET));
					r.getHeaders().put(Http.CONTENT_LENGTH, String.valueOf(bb.remaining()));
					write.write(r);
					write.handle(null, bb);
					write.close();
				}
			}
		};
		
		switch (request.getMethod()) {
		case GET:
			handler.get(path, parameters, callback);
			break;
		case DELETE:
			handler.delete(path, parameters, callback);
			break;
		case HEAD:
			handler.head(path, parameters, callback);
			break;
		case PUT:
			handler.put(path, parameters, callback);
			break;
		case POST:
			handler.post(path, parameters, post, callback);
			break;
		default:
			HttpResponse r = new HttpResponse(Http.Status.INTERNAL_SERVER_ERROR, Http.Message.INTERNAL_SERVER_ERROR);
			write.write(r);
			write.close();
			break;
		}
		request = null;
		post = null;
	}
	
	@Override
	public void failed(IOException e) {
		LOGGER.trace("Client connection failed, probably closed by peer during transfer", e);
	}
	
	@Override
	public void close() {
		LOGGER.trace("Connection closed by peer");
	}

}
