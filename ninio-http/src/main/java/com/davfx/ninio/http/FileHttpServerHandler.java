package com.davfx.ninio.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
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
	private final HttpQueryPath rootPath;
	private HttpRequest request;
	private final Map<String, HttpHeaderValue> contentTypes = new LinkedHashMap<>();
	private String index = DEFAULT_INDEX;
	
	public FileHttpServerHandler(File dir, HttpQueryPath rootPath) {
		this.dir = dir;
		this.rootPath = rootPath;
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
			List<String> ll = new LinkedList<>();
			Iterator<String> i = rootPath.path.iterator();
			for (String s : request.path.path.path) {
				if (!i.hasNext()) {
					i = null;
				}
				if (i == null) {
					ll.add(s);
				} else {
					String n = i.next();
					if (!s.equals(n)) {
						write.write(new HttpResponse(HttpStatus.NOT_FOUND, HttpMessage.NOT_FOUND));
						write.close();
						return;
					}
				}
			}

			File file = new File(dir, ll.isEmpty() ? index : (File.separatorChar + Joiner.on(File.separatorChar).join(ll)));

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
