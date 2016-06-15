package com.davfx.ninio.http.service;

import java.nio.charset.Charset;

import com.google.common.base.Charsets;

public final class HttpContentType {

	private HttpContentType() {
	}
	
	private static String charsetExtension(Charset c) {
		return "; charset=" + c.name();
	}
	
	public static String plainText(Charset c) {
		return "text/plain" + charsetExtension(c);
	}
	public static String plainText() {
		return plainText(Charsets.UTF_8);
	}
	public static String html(Charset c) {
		return "text/html" + charsetExtension(c);
	}
	public static String html() {
		return html(Charsets.UTF_8);
	}
	public static String json(Charset c) {
		return "application/json" + charsetExtension(c);
	}
	public static String json() {
		return json(Charsets.UTF_8);
	}
	public static String wwwFormUrlEncoded() {
		return "application/x-www-form-urlencoded";
	}
	
	public static Charset getContentType(String value) {
		int i = value.indexOf(';');
		if (i < 0) {
			return null;
		}

		String s = value.substring(i + 1).trim();
		if (!s.startsWith("charset=")) {
			return null;
		}
		return Charset.forName(s.substring("charset=".length()));
	}
}
