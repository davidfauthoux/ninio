package com.davfx.ninio.http.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.http.Http;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpServerHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;

public final class JsonDirectoryHttpServerHandler implements HttpServerHandler {
	private final File dir;
	private HttpRequest request;
	
	public JsonDirectoryHttpServerHandler(File dir) {
		this.dir = dir;
	}
	
	@Override
	public void close() {
	}
	@Override
	public void failed(IOException e) {
	}
	@Override
	public void handle(Address address, ByteBuffer buffer) {
	}
	@Override
	public void handle(HttpRequest request) {
		this.request = request;
	}
	@Override
	public void ready(Write write) {
		HttpResponse r = new HttpResponse(Http.Status.OK, Http.Message.OK);
		r.getHeaders().put("Cache-Control", "private, max-age=0, no-cache");
		try {
			String path = request.getPath();
			int k = path.indexOf('?');
			if (k >= 0) {
				path = path.substring(0, k);
			}
			if (path.endsWith("/")) {
				path = path.substring(0, path.length() - "/".length());
			}
			File d = new File(dir.getCanonicalPath() + Http.Url.decode(path));
			if (d.isDirectory()) {
				JsonArray a = new JsonArray();
				File[] files = d.listFiles();
				if (files != null) {
					for (File f : files) {
						a.add(new JsonPrimitive(f.getName() + (f.isDirectory() ? "/" : "")));
					}
				}

				r.getHeaders().put(Http.CONTENT_TYPE, Http.ContentType.JSON);
				ByteBuffer bb = ByteBuffer.wrap(a.toString().getBytes(Http.UTF8_CHARSET));
				r.getHeaders().put(Http.CONTENT_LENGTH, String.valueOf(bb.remaining()));
				write.write(r);
				write.handle(null, bb);
				write.close();
			} else {
				r.getHeaders().put(Http.CONTENT_LENGTH, String.valueOf(d.length()));
				write.write(r);
				try (InputStream in = new FileInputStream(d)) {
					while (true) {
						byte[] b = new byte[10 * 1024];
						int l = in.read(b);
						if (l < 0) {
							break;
						}
						write.handle(null, ByteBuffer.wrap(b, 0, l));
					}
				}
				write.close();
			}
		} catch (IOException ioe) {
			write.write(new HttpResponse(Http.Status.INTERNAL_SERVER_ERROR, Http.Message.INTERNAL_SERVER_ERROR));
			write.close();
		}
	}
}
