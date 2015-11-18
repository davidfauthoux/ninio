package com.davfx.ninio.http;

import java.util.List;

import com.google.common.base.Splitter;


public final class HttpHeaderExtension {
	private HttpHeaderExtension() {
	}
	
	public static String append(String k, String c) {
		return HttpSpecification.EXTENSION_SEPARATOR + k + HttpSpecification.PARAMETER_KEY_VALUE_SEPARATOR + c;
	}

	public static String extract(String h, String k) {
		int i = h.indexOf(HttpSpecification.EXTENSION_SEPARATOR);
		if (i < 0) {
			return null;
		}
		List<String> l = Splitter.on(HttpSpecification.PARAMETER_KEY_VALUE_SEPARATOR).splitToList(h.substring(i).trim());
		if ((l.size() == 2) && (l.get(0).equals(k))) {
			return l.get(0).trim();
		}
		return null;
	}

}
