package com.davfx.ninio.http.util;

import java.io.OutputStream;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.Failable;
import com.davfx.ninio.http.HttpContentType;
import com.davfx.ninio.http.HttpHeaderKey;
import com.davfx.ninio.http.HttpHeaderValue;
import com.davfx.ninio.http.HttpMessage;
import com.davfx.ninio.http.HttpStatus;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

public interface HttpController {
	interface HttpStream {
		void produce(OutputStream output) throws Exception;
	}
	
	interface HttpAsyncOutput extends Failable, Closeable {
		HttpAsyncOutput ok();
		HttpAsyncOutput internalServerError();
		HttpAsyncOutput notFound();
		HttpAsyncOutput header(String key, HttpHeaderValue value);
		HttpAsyncOutput contentType(HttpHeaderValue contentType);
		HttpAsyncOutput contentLength(long contentLength);
		HttpAsyncOutput produce(ByteBuffer buffer);
		HttpAsyncOutput produce(String buffer);
	}
	interface HttpAsync {
		void produce(HttpAsyncOutput output);
	}

	interface HttpWrap {
		void handle(Http http) throws Exception;
	}
	
	final class Http {
		final int status;
		final String reason;
		final Multimap<String, HttpHeaderValue> headers = LinkedHashMultimap.create();
		String content = null;
		HttpStream stream = null;
		HttpAsync async = null;
		final HttpWrap wrap;
		
		private Http(int status, String reason, HttpWrap wrap, HttpAsync async) {
			this.status = status;
			this.reason = reason;
			this.wrap = wrap;
			this.async = async;
			headers.put(HttpHeaderKey.CONTENT_TYPE, HttpContentType.plainText());
		}
		private Http(int status, String reason) {
			this(status, reason, null, null);
		}
		private Http(int status, String reason, HttpWrap wrap) {
			this(status, reason, wrap, null);
		}
		
		public Http header(String key, HttpHeaderValue value) {
			headers.put(key, value);
			return this;
		}
		// Last one
		public HttpHeaderValue header(String key) {
			HttpHeaderValue value = null;
			for (HttpHeaderValue v : headers.get(key)) {
				value = v;
			}
			return value;
		}
		
		public Http contentType(HttpHeaderValue contentType) {
			header(HttpHeaderKey.CONTENT_TYPE, contentType);
			return this;
		}
		public HttpHeaderValue contentType() {
			return header(HttpHeaderKey.CONTENT_TYPE);
		}
		
		public Http contentLength(long contentLength) {
			header(HttpHeaderKey.CONTENT_LENGTH, HttpHeaderValue.simple(String.valueOf(contentLength)));
			return this;
		}
		public long contentLength() {
			HttpHeaderValue v = header(HttpHeaderKey.CONTENT_LENGTH);
			if (v == null) {
				return -1L;
			}
			return v.asLong();
		}
		
		public Http content(String content) {
			this.content = content;
			stream = null;
			async = null;
			return this;
		}
		public String content() {
			return content;
		}
		public Http stream(HttpStream stream) {
			content = null;
			async = null;
			this.stream = stream;
			return this;
		}
		public HttpStream stream() {
			return stream;
		}
		public Http async(HttpAsync async) {
			content = null;
			stream = null;
			this.async = async;
			return this;
		}
		public HttpAsync async() {
			return async;
		}
		
		public static Http ok() {
			return new Http(HttpStatus.OK, HttpMessage.OK);
		}
		public static Http internalServerError() {
			return new Http(HttpStatus.INTERNAL_SERVER_ERROR, HttpMessage.INTERNAL_SERVER_ERROR);
		}
		public static Http notFound() {
			return new Http(HttpStatus.NOT_FOUND, HttpMessage.NOT_FOUND);
		}
		
		public static Http wrap(HttpWrap wrap) {
			return new Http(HttpStatus.OK, HttpMessage.OK, wrap);
		}

	}
}
