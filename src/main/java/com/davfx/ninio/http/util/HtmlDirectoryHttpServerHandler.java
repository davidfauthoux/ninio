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

public final class HtmlDirectoryHttpServerHandler implements HttpServerHandler {
	private final File dir;
	private HttpRequest request;
	
	public HtmlDirectoryHttpServerHandler(File dir) {
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
			HttpQuery q = new HttpQuery(request.getPath());
			String path = q.getPath();
			File file = q.getFile(dir, null);

			if (file == null) {
				write.write(new HttpResponse(Http.Status.FORBIDDEN, Http.Message.FORBIDDEN));
				write.close();
				return;
			}

			if (file.isDirectory()) {
				StringBuilder b = new StringBuilder();
				b.append("<!doctype html>");
				b.append("<html>");
				b.append("<head>");
				b.append("<meta charset=\"utf-8\">");
				b.append("</head>");
				b.append("<body>");

				File[] files = file.listFiles();
				if (files != null) {
					b.append("<ul>");
					if (!path.isEmpty()) {
						int k = path.lastIndexOf('/');
						String parent;
						if (k == 0) {
							parent = "/";
						} else {
							parent = path.substring(0, k);
						}
						b.append("<li>");
						b.append("<a href=\"").append(parent).append("\">");
						b.append("..");
						b.append("</a>");
						b.append("</li>");
					}
					for (File f : files) {
						b.append("<li>");
						b.append("<a href=\"").append(path + "/" + Http.Url.encode(f.getName())).append("\">");
						b.append(f.getName());
						b.append("</a>");
						b.append("</li>");
					}
					b.append("</ul>");
				}

				b.append("</body>");
				b.append("</html>");
				ByteBuffer bb = ByteBuffer.wrap(b.toString().getBytes(Http.UTF8_CHARSET));
				r.getHeaders().put(Http.CONTENT_LENGTH, String.valueOf(bb.remaining()));
				write.write(r);
				write.handle(null, bb);
				write.close();
			} else {
				r.getHeaders().put(Http.CONTENT_LENGTH, String.valueOf(file.length()));
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
			}
		} catch (IOException ioe) {
			write.write(new HttpResponse(Http.Status.INTERNAL_SERVER_ERROR, Http.Message.INTERNAL_SERVER_ERROR));
			write.close();
		}
	}
}
