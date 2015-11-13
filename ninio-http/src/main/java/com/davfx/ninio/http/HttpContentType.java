package com.davfx.ninio.http;

import com.google.common.base.Charsets;

public interface HttpContentType {

	String TEXT = "text/plain; charset=" + Charsets.UTF_8.name();
	String JSON = "application/json; charset=" + Charsets.UTF_8.name();
	String JAVASCRIPT = "application/javascript; charset=" + Charsets.UTF_8.name();
	String WWW_FORM_URL_ENCODED = "application/x-www-form-urlencoded";

}
