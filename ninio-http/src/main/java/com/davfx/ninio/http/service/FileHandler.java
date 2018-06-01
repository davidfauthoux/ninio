package com.davfx.ninio.http.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.http.HttpListening;
import com.davfx.ninio.http.dependencies.Dependencies;
import com.davfx.ninio.http.service.HttpController.Http;
import com.davfx.ninio.util.ConfigUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;

public final class FileHandler {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(FileHandler.class);
	
	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(HttpListening.class.getPackage().getName());
	
	private static final ImmutableMap<String, String> DEFAULT_CONTENT_TYPES;
	static {
		ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
		for (Config c : CONFIG.getConfigList("file.contentTypes")) {
			b.put(c.getString("extension").toLowerCase(), c.getString("contentType"));
		}
		DEFAULT_CONTENT_TYPES = b.build();
	}
	
	private final File dir;
	private final String index;
	
	public FileHandler(File dir, String index) {
		this.dir = dir;
		this.index = index;
	}
	
	public Http handle(ImmutableList<String> path) {
		List<String> p = new LinkedList<>(path);
		String rootName = Joiner.on('/').join(p);
		p.add(index);
		String indexName = Joiner.on('/').join(p);
		
		String name = indexName;
		LOGGER.debug("Resources dir: {}", dir);
		File f = new File(dir, name);
		InputStream in;
		if (f.exists()) {
			try {
				in = new FileInputStream(f);
			} catch (IOException e) {
				in = null;
			}
		} else {
			LOGGER.debug("Resource not found: {}", name);
			name = rootName;
			f = new File(dir, name);
			try {
				in = new FileInputStream(f);
			} catch (IOException e) {
				in = null;
			}
		}

		if (in != null) {
			String contentType = null;
			for (Map.Entry<String, String> e : DEFAULT_CONTENT_TYPES.entrySet()) {
				if (name.toLowerCase().endsWith(e.getKey())) {
					contentType = e.getValue();
					break;
				}
			}
			
			if (contentType == null) {
				LOGGER.debug("Resource found with no content type: {}", name);
			} else {
				LOGGER.debug("Resource found: {} [{}]", name, contentType);
			}

			// "Cache-Control", "private, max-age=0, no-cache"

			return Http.ok().contentType(contentType).stream(in);
		} else {
			LOGGER.debug("Resource not found: {}", name);
			return Http.notFound();
		}
	}
}
