package com.davfx.ninio.http;

public interface HttpHeaderValue {
	
	String GZIP = "gzip";
	String CLOSE = "close";
	String KEEP_ALIVE = "keep-alive";
	String CHUNKED = "chunked";
	String CHARSET = "charset";

	String ACCESS_CONTROL_ALLOWED_METHODS = "GET, PUT, POST, DELETE, HEAD";

	String DEFAULT_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/38.0.2125.111 Safari/537.36";
	String DEFAULT_ACCEPT = "*/*";

	String QUALITY = "q";
	String WILDCARD = "*";

}
