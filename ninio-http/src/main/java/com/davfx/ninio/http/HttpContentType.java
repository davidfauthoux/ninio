package com.davfx.ninio.http;

import java.nio.charset.Charset;

import com.google.common.base.Charsets;

public final class HttpContentType {

	private HttpContentType() {
	}
	
	public static String plainText(Charset c) {
		return "text/plain" + HttpHeaderExtension.append(HttpHeaderKey.CHARSET, c.name());
	}
	public static String plainText() {
		return plainText(Charsets.UTF_8);
	}
	public static String json(Charset c) {
		return "application/json" + HttpHeaderExtension.append(HttpHeaderKey.CHARSET, c.name());
	}
	public static String json() {
		return json(Charsets.UTF_8);
	}
	public static String wwwFormUrlEncoded() {
		return "application/x-www-form-urlencoded";
	}
}
