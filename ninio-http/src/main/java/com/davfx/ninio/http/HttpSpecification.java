package com.davfx.ninio.http;

public interface HttpSpecification {
	String HTTP10 = "HTTP/1.0";
	String HTTP11 = "HTTP/1.1";
	
	char CR = '\r';
	char LF = '\n';
	
	char START_LINE_SEPARATOR = ' ';
	
	char HEADER_KEY_VALUE_SEPARATOR = ':';
	char HEADER_BEFORE_VALUE = ' ';
	//%% char EXTENSION_SEPARATOR = ';';
	//%% char MULTIPLE_SEPARATOR = ',';

	char PARAMETERS_START = '?';
	char PARAMETERS_SEPARATOR = '&';
	char PARAMETER_KEY_VALUE_SEPARATOR = '=';
	char HASH_SEPARATOR = '#';
	char PORT_SEPARATOR = ':';
	char PATH_SEPARATOR = '/';

	String PROTOCOL = "http://";
	String SECURE_PROTOCOL = "https://";
	
	int DEFAULT_PORT = 80;
	int DEFAULT_SECURE_PORT = 443;
}
