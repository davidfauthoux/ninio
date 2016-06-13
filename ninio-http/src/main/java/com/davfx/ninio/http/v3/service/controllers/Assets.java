package com.davfx.ninio.http.v3.service.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;

import com.davfx.ninio.http.v3.HttpMethod;
import com.davfx.ninio.http.v3.service.HttpController;
import com.davfx.ninio.http.v3.service.HttpServiceRequest;
import com.davfx.ninio.http.v3.service.annotations.Route;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class Assets implements HttpController {
	
	private static final Config CONFIG = ConfigFactory.load(Assets.class.getClassLoader());
	
	private static final ImmutableMap<String, String> DEFAULT_CONTENT_TYPES;
	static {
		ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
		for (Config c : CONFIG.getConfigList("ninio.http.file.contentTypes")) {
			b.put(c.getString("extension").toLowerCase(), c.getString("contentType"));
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
	public Http serve(HttpServiceRequest request) throws Exception {
		final File file = new File(dir, request.path.isEmpty() ? index : Joiner.on(File.separatorChar).join(request.path));

		if (file.isFile()) {
			String name = file.getName();
			String contentType = null;
			for (Map.Entry<String, String> e : DEFAULT_CONTENT_TYPES.entrySet()) {
				if (name.toLowerCase().endsWith(e.getKey())) {
					contentType = e.getValue();
					break;
				}
			}

			// "Cache-Control", "private, max-age=0, no-cache"

			return Http.ok().contentType(contentType).contentLength(file.length()).stream(new FileInputStream(file));
		} else {
			return Http.notFound();
		}
	}
}
