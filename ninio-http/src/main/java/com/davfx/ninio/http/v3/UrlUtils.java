package com.davfx.ninio.http.v3;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

public final class UrlUtils {

	private static final String UTF8 = "UTF-8";
	private static final char SPACE_ENCODED_PLUS = '+';
	private static final String SPACE_ENCODED_NUMBER = "%20";
	
	private UrlUtils() {
	}
	
	public static String encode(String component) {
		if (component == null) {
			return null;
		}
		try {
			return URLEncoder.encode(component, UTF8).replaceAll("\\" + SPACE_ENCODED_PLUS, SPACE_ENCODED_NUMBER);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static String decode(String component) {
		if (component == null) {
			return null;
		}
		try {
			return URLDecoder.decode(component, UTF8);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
}
