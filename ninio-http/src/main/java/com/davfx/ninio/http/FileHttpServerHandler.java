package com.davfx.ninio.http;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import com.davfx.ninio.core.Address;

public final class FileHttpServerHandler implements HttpServerHandler {
	
	public static interface File {
		String contentType();
		InputStream open();
	}
	
	private HttpRequest request;
	private final Map<String, File> content = new LinkedHashMap<>();
	
	public FileHttpServerHandler() {
	}
	
	public FileHttpServerHandler add(String path, File content) {
		content.put(path, content);
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
			String path;
			
			File file = new HttpQuery(request.getPath()).getFile(dir, index);

			if (file == null) {
				write.write(new HttpResponse(Http.Status.FORBIDDEN, Http.Message.FORBIDDEN));
				write.close();
				return;
			}

			if (file.isFile()) {
				String name = file.getName();
				String contentType = null;
				for (Map.Entry<String, String> e : contentTypes.entrySet()) {
					if (name.endsWith(e.getKey())) {
						contentType = e.getValue();
						break;
					}
				}
				
				HttpResponse r = new HttpResponse(Http.Status.OK, Http.Message.OK);
				// r.getHeaders().put("Cache-Control", "private, max-age=0, no-cache");
				r.getHeaders().put(Http.CONTENT_LENGTH, String.valueOf(file.length()));
				if (contentType != null) {
					r.getHeaders().put(Http.CONTENT_TYPE, contentType);
				}
				write.write(r);
				try (InputStream in = new FileInputStream(file)) {
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
