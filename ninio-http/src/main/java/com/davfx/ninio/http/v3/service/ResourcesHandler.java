package com.davfx.ninio.http.v3.service;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.http.v3.HttpListening;
import com.davfx.ninio.http.v3.service.HttpController.Http;
import com.davfx.util.ConfigUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;

public final class ResourcesHandler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ResourcesHandler.class);
	
	private static final Config CONFIG = ConfigUtils.load(HttpListening.class);
	
	private static final ImmutableMap<String, String> DEFAULT_CONTENT_TYPES;
	static {
		ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
		for (Config c : CONFIG.getConfigList("ninio.http.file.contentTypes")) {
			b.put(c.getString("extension").toLowerCase(), c.getString("contentType"));
		}
		DEFAULT_CONTENT_TYPES = b.build();
	}
	
	private final Class<?> clazz;
	private final boolean rooted;
	private final List<String> dir;
	private final String index;
	
	// dir uses '/' and does not end with '/'
	public ResourcesHandler(Class<?> clazz, String dir, String index) {
		this.clazz = clazz;
		if (dir.startsWith("/")) {
			dir = dir.substring("/".length());
			rooted = true;
		} else {
			rooted = false;
		}
		this.dir = dir.isEmpty() ? new LinkedList<String>() : Splitter.on('/').splitToList(dir);
		this.index = index;
	}
	
	public Http handle(ImmutableList<String> path) {
		List<String> p = new LinkedList<>(dir);
		p.addAll(path);
		String rootName = Joiner.on('/').join(p);
		p.add(index);
		String indexName = Joiner.on('/').join(p);
		
		if (rooted) {
			rootName = '/' + rootName;
			indexName = '/' + indexName;
		}
		
		String name = indexName;
		InputStream in = clazz.getResourceAsStream(name);
		if (in == null) {
			LOGGER.debug("Resource not found: {}", name);
			name = rootName;
			in = clazz.getResourceAsStream(name);
		}

		if (in != null) {
			LOGGER.debug("Resource found: {}", name);
			String contentType = null;
			for (Map.Entry<String, String> e : DEFAULT_CONTENT_TYPES.entrySet()) {
				if (name.toLowerCase().endsWith(e.getKey())) {
					contentType = e.getValue();
					break;
				}
			}

			// "Cache-Control", "private, max-age=0, no-cache"

			return Http.ok().contentType(contentType).stream(in);
		} else {
			LOGGER.debug("Resource not found: {}", name);
			return Http.notFound();
		}
	}
}
