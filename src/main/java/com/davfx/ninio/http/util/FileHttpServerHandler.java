package com.davfx.ninio.http.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.http.Http;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpServerHandler;

public final class FileHttpServerHandler implements HttpServerHandler {
	private final File dir;
	private HttpRequest request;
	private final Map<String, String> contentTypes = new LinkedHashMap<>();
	private String index = null;
	
	public FileHttpServerHandler(File dir) {
		this.dir = dir;
	}
	
	public FileHttpServerHandler setContentType(String extension, String contentType) {
		contentTypes.put(extension, contentType);
		return this;
	}
	public FileHttpServerHandler setIndex(String index) {
		this.index = index;
		return this;
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
		try {
			String root = dir.getCanonicalPath();
			String path = new HttpQuery(request.getPath()).getPath();
			
			if ((index != null) && (path.charAt(path.length() - 1) == Http.PATH_SEPARATOR)) {
				path = path + index;
			}

			File d = new File(root + path);
			if (d.isFile()) {
				if (!d.getCanonicalPath().startsWith(root)) {
					write.write(new HttpResponse(Http.Status.FORBIDDEN, Http.Message.FORBIDDEN));
					write.close();
					return;
				}
				String name = d.getName();
				String contentType = null;
				for (Map.Entry<String, String> e : contentTypes.entrySet()) {
					if (name.endsWith(e.getKey())) {
						contentType = e.getValue();
						break;
					}
				}
				
				HttpResponse r = new HttpResponse(Http.Status.OK, Http.Message.OK);
				// r.getHeaders().put("Cache-Control", "private, max-age=0, no-cache");
				r.getHeaders().put(Http.CONTENT_LENGTH, String.valueOf(d.length()));
				if (contentType != null) {
					r.getHeaders().put(Http.CONTENT_TYPE, contentType);
				}
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
			} else {
				write.write(new HttpResponse(Http.Status.NOT_FOUND, Http.Message.NOT_FOUND));
				write.close();
			}
		} catch (IOException ioe) {
			write.write(new HttpResponse(Http.Status.INTERNAL_SERVER_ERROR, Http.Message.INTERNAL_SERVER_ERROR));
			write.close();
		}
	}
}
