package com.davfx.ninio.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.davfx.ninio.core.Address;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMultimap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class FileHttpServerHandler implements HttpServerHandler {
	
	private static final Config CONFIG = ConfigFactory.load(FileHttpServerHandler.class.getClassLoader());
	private static final String DEFAULT_INDEX = CONFIG.getString("ninio.http.file.index");
	private static final int BUFFER_SIZE = CONFIG.getBytes("ninio.http.file.buffer").intValue();
	
	private static final Map<String, HttpHeaderValue> DEFAULT_CONTENT_TYPES = new HashMap<>();
	static {
		for (Config c : CONFIG.getConfigList("ninio.http.file.contentTypes")) {
			DEFAULT_CONTENT_TYPES.put(c.getString("extension").toLowerCase(), HttpHeaderValue.of(c.getString("contentType")));
		}
	}
	
	private final File dir;
	private HttpRequest request;
	private final Map<String, HttpHeaderValue> contentTypes = new LinkedHashMap<>();
	private String index = DEFAULT_INDEX;
	
	public FileHttpServerHandler(File dir) {
		this.dir = dir;
		contentTypes.putAll(DEFAULT_CONTENT_TYPES);
	}
	
	public FileHttpServerHandler withContentType(String extension, HttpHeaderValue contentType) {
		contentTypes.put(extension.toLowerCase(), contentType);
		return this;
	}
	public FileHttpServerHandler withIndex(String index) {
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
			File file = new File(dir, request.path.path.path.isEmpty() ? index : (File.separatorChar + Joiner.on(File.separatorChar).join(request.path.path.path)));

			if (file.isFile()) {
				String name = file.getName();
				HttpHeaderValue contentType = null;
				for (Map.Entry<String, HttpHeaderValue> e : contentTypes.entrySet()) {
					if (name.toLowerCase().endsWith(e.getKey())) {
						contentType = e.getValue();
						break;
					}
				}
				
				ImmutableMultimap.Builder<String, HttpHeaderValue> parameters = ImmutableMultimap.builder();
				parameters.put(HttpHeaderKey.CONTENT_LENGTH, HttpHeaderValue.simple(String.valueOf(file.length())));
				if (contentType != null) {
					parameters.put(HttpHeaderKey.CONTENT_TYPE, contentType);
				}
				// "Cache-Control", "private, max-age=0, no-cache"

				write.write(new HttpResponse(HttpStatus.OK, HttpMessage.OK, parameters.build()));
				try (InputStream in = new FileInputStream(file)) {
					while (true) {
						byte[] b = new byte[BUFFER_SIZE];
						int l = in.read(b);
						if (l < 0) {
							break;
						}
						write.handle(null, ByteBuffer.wrap(b, 0, l));
					}
				}
				write.close();
			} else {
				write.write(new HttpResponse(HttpStatus.NOT_FOUND, HttpMessage.NOT_FOUND));
				write.close();
			}
		} catch (IOException ioe) {
			write.write(new HttpResponse(HttpStatus.INTERNAL_SERVER_ERROR, HttpMessage.INTERNAL_SERVER_ERROR));
			write.close();
		}
	}
}
