package com.davfx.ninio.http.util;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.http.Http;
import com.google.common.base.Splitter;


public final class HttpQuery {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpQuery.class);
	
	private final String path;
	private final Map<String, String> parameters = new LinkedHashMap<String, String>();

	public HttpQuery(String httpPath) {
		int i = httpPath.indexOf('?');
		String p;
		if (i < 0) {
			p = httpPath;
		} else {
			p = httpPath.substring(0, i);
			for (String kv : Splitter.on('&').split(httpPath.substring(i + 1))) {
				if (kv.isEmpty()) {
					continue;
				}
				List<String> j = Splitter.on('=').splitToList(kv);
				if (j.size() == 2) {
					parameters.put(j.get(0), Http.Url.decode(j.get(1)));
				} else {
					parameters.put(kv, null);
				}
			}
		}

		if (p.isEmpty()) {
			path = String.valueOf(Http.PATH_SEPARATOR);
		} else {
			path = Http.Url.decode(p);
		}
	}
	
	public String getPath() {
		return path;
	}
	
	public File getFile(File root, String index) {
		File d = new File(root + Http.Url.decode(path).replace(Http.PATH_SEPARATOR, File.separatorChar));

		try {
			if (!d.getCanonicalPath().startsWith(root.getCanonicalPath())) {
				return null;
			}
		} catch (IOException ioe) {
			LOGGER.error("Could not check file request", ioe);
			return null;
		}

		if ((index != null) && d.isDirectory()) {
			d = new File(d, index);
		}
		return d;
	}
	
	public Parameters getParameters() {
		return new Parameters() {
			@Override
			public Iterable<String> keys() {
				return parameters.keySet();
			}
			
			@Override
			public String getValue(String key) {
				return parameters.get(key);
			}
			
			@Override
			public String toString() {
				return parameters.toString();
			}
		};
	}
}
