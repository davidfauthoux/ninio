package com.davfx.ninio.http.util.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import com.davfx.ninio.http.HttpHeaderValue;
import com.davfx.ninio.http.HttpMethod;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.util.HttpController;
import com.davfx.ninio.http.util.annotations.Route;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class Assets implements HttpController{
	
	private static final Config CONFIG = ConfigFactory.load(Assets.class.getClassLoader());
	private static final int BUFFER_SIZE = CONFIG.getBytes("ninio.http.file.buffer").intValue();
	
	private static final ImmutableMap<String, HttpHeaderValue> DEFAULT_CONTENT_TYPES;
	static {
		ImmutableMap.Builder<String, HttpHeaderValue> b = ImmutableMap.builder();
		for (Config c : CONFIG.getConfigList("ninio.http.file.contentTypes")) {
			b.put(c.getString("extension").toLowerCase(), HttpHeaderValue.of(c.getString("contentType")));
		}
		DEFAULT_CONTENT_TYPES = b.build();
	}
	
	private final File dir;
	private final String index;
	
	public Assets(File dir, String index) {
		this.dir = dir;
		this.index = index;
	}
	
	@Route(method = HttpMethod.GET)
	public Http serve(HttpRequest request) throws Exception {
		final File file = new File(dir, request.path.path.path.isEmpty() ? index : Joiner.on(File.separatorChar).join(request.path.path.path));

		if (file.isFile()) {
			String name = file.getName();
			HttpHeaderValue contentType = null;
			for (Map.Entry<String, HttpHeaderValue> e : DEFAULT_CONTENT_TYPES.entrySet()) {
				if (name.toLowerCase().endsWith(e.getKey())) {
					contentType = e.getValue();
					break;
				}
			}

			// "Cache-Control", "private, max-age=0, no-cache"

			return Http.ok().contentType(contentType).contentLength(file.length()).stream(new HttpController.HttpStream() {
				@Override
				public void produce(OutputStream out) throws Exception {
					try (InputStream in = new FileInputStream(file)) {
						while (true) {
							byte[] b = new byte[BUFFER_SIZE];
							int l = in.read(b);
							if (l < 0) {
								break;
							}
							out.write(b, 0, l);
						}
					}
				}
			});
		} else {
			return Http.notFound();
		}
	}
}
