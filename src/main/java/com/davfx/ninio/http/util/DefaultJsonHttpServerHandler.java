package com.davfx.ninio.http.util;

public class DefaultJsonHttpServerHandler implements JsonHttpServerHandler {
	public DefaultJsonHttpServerHandler() {
	}
	
	@Override
	public void put(String path, Parameters parameters, Callback callback) {
		callback.send(null);
	}
	
	@Override
	public void delete(String path, Parameters parameters, Callback callback) {
		callback.send(null);
	}
	
	@Override
	public void head(String path, Parameters parameters, Callback callback) {
		callback.send(null);
	}
	
	@Override
	public void post(String path, Parameters parameters, InMemoryPost post, Callback callback) {
		callback.send(null);
	}

	@Override
	public void get(String path, Parameters parameters, Callback callback) {
		callback.send(null);
	}
}
