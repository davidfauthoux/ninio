package com.davfx.ninio.http.util;

import com.google.gson.JsonElement;

public interface JsonHttpServerHandler {
	interface Callback {
		void send(JsonElement response);
	}
	void get(String path, Parameters parameters, Callback callback);
	void head(String path, Parameters parameters, Callback callback);
	void delete(String path, Parameters parameters, Callback callback);
	void post(String path, Parameters parameters, InMemoryPost post, Callback callback);
	void put(String path, Parameters parameters, Callback callback);
}