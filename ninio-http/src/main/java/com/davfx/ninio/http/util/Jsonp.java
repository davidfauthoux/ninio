package com.davfx.ninio.http.util;

import com.davfx.ninio.http.HttpContentType;
import com.davfx.ninio.http.HttpHeaderValue;
import com.davfx.ninio.http.HttpMethod;
import com.davfx.ninio.http.util.annotations.QueryParameter;
import com.davfx.ninio.http.util.annotations.Route;

public final class Jsonp implements HttpController {
	public Jsonp() {
	}
	
	@Route(method = HttpMethod.GET)
	public Http checkMessage(final @QueryParameter("jsonp") String jsonp) {
		if (jsonp == null) {
			return null;
		}
		return Http.wrap(new HttpWrap() {
			@Override
			public void handle(Http http) throws Exception {
				if (http.contentType().contains(HttpContentType.json().asString())) {
					http.contentType(HttpHeaderValue.simple("application/javascript")).content(jsonp + "(" + http.content() + ");");
				}
			}
		});
	}

}
