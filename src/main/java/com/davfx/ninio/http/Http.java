package com.davfx.ninio.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;

public interface Http {
	String HTTP10 = "HTTP/1.0";
	String HTTP11 = "HTTP/1.1";
	
	Charset USASCII_CHARSET = Charset.forName("US-ASCII");
	Charset DEFAULT_CHARSET = Charset.forName("ISO-8859-1");
	Charset UTF8_CHARSET = Charset.forName("UTF-8");

	char CR = '\r';
	char LF = '\n';
	
	String CONTENT_LENGTH = "Content-Length";
	String CONTENT_ENCODING = "Content-Encoding";
	String CONTENT_TYPE = "Content-Type";
	String ACCEPT_ENCODING = "Accept-Encoding";
	String TRANSFER_ENCODING = "Transfer-Encoding";
	String HOST = "Host";
	String CONNECTION = "Connection";
	String GZIP = "gzip";
	String CLOSE = "close";
	String KEEP_ALIVE = "keep-alive";
	String CHUNKED = "chunked";
	String CHARSET = "charset";
	String LOCATION = "Location";
	
	String QUALITY = "q";
	String WILDCARD = "*";

	interface ContentType {
		String TEXT = "text/plain; charset=" + UTF8_CHARSET.name();
		String JSON = "application/json; charset=" + UTF8_CHARSET.name();
		String WWW_FORM_URL_ENCODED = "application/x-www-form-urlencoded";
	}

	char PARAMETERS_START = '?';
	char PARAMETERS_SEPARATOR = '&';
	char PARAMETER_KEY_VALUE_SEPARATOR = '=';
	char PORT_SEPARATOR = ':';
	char PATH_SEPARATOR = '/';

	char START_LINE_SEPARATOR = ' ';
	
	char HEADER_KEY_VALUE_SEPARATOR = ':';
	char HEADER_BEFORE_VALUE = ' ';
	char EXTENSION_SEPARATOR = ';';
	char MULTIPLE_SEPARATOR = ',';

	String PROTOCOL = "http://";
	String SECURE_PROTOCOL = "https://";
	int DEFAULT_PORT = 80;
	int DEFAULT_SECURE_PORT = 443;
	
	interface Status {
		int OK = 200;
		int INTERNAL_SERVER_ERROR = 500;
	}
	interface Message {
		String OK = "OK";
		String INTERNAL_SERVER_ERROR = "Internal Server Error";
	}
	
	public static final class Url {
		private static final String UTF8 = "UTF-8";
		private static final char SPACE_ENCODED_PLUS = '+';
		private static final String SPACE_ENCODED_NUMBER = "%20";
		private Url() {
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
	
}
