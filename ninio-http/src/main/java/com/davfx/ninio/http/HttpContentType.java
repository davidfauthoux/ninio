package com.davfx.ninio.http;

import java.nio.charset.Charset;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;

public final class HttpContentType {

	private HttpContentType() {
	}
	
	public static HttpHeaderValue plainText(Charset c) {
		return new HttpHeaderValue(ImmutableList.of("text/plain"), ImmutableMultimap.of(HttpHeaderKey.CHARSET, Optional.of(c.name())));
	}
	public static HttpHeaderValue plainText() {
		return plainText(Charsets.UTF_8);
	}
	public static HttpHeaderValue html(Charset c) {
		return new HttpHeaderValue(ImmutableList.of("text/html"), ImmutableMultimap.of(HttpHeaderKey.CHARSET, Optional.of(c.name())));
	}
	public static HttpHeaderValue html() {
		return html(Charsets.UTF_8);
	}
	public static HttpHeaderValue json(Charset c) {
		return new HttpHeaderValue(ImmutableList.of("application/json"), ImmutableMultimap.of(HttpHeaderKey.CHARSET, Optional.of(c.name())));
	}
	public static HttpHeaderValue json() {
		return json(Charsets.UTF_8);
	}
	public static HttpHeaderValue wwwFormUrlEncoded() {
		return HttpHeaderValue.simple("application/x-www-form-urlencoded");
	}
}
