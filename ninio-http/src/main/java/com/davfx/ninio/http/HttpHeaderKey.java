package com.davfx.ninio.http;

interface HttpHeaderKey {
	String CONTENT_LENGTH = "Content-Length";
	String CONTENT_ENCODING = "Content-Encoding";
	String CONTENT_TYPE = "Content-Type";
	String ACCEPT_ENCODING = "Accept-Encoding";
	String TRANSFER_ENCODING = "Transfer-Encoding";
	String HOST = "Host";
	String CONNECTION = "Connection";
	String LOCATION = "Location";
	String USER_AGENT = "User-Agent";
	String ACCEPT = "Accept";

	String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
	String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";

}
