package com.davfx.ninio.http.v3.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import com.davfx.ninio.http.v3.HttpHeaderKey;
import com.davfx.ninio.http.v3.HttpMessage;
import com.davfx.ninio.http.v3.HttpStatus;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

public interface HttpController {
	interface HttpAsyncOutput {
		HttpAsyncOutput ok();
		HttpAsyncOutput internalServerError();
		HttpAsyncOutput notFound();
		HttpAsyncOutput header(String key, String value);
		HttpAsyncOutput contentType(String contentType);
		HttpAsyncOutput contentLength(long contentLength);
		HttpAsyncOutput produce(ByteBuffer buffer);
		HttpAsyncOutput produce(String buffer);
		void finish();
		void cancel();
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
		final Multimap<String, String> headers = LinkedHashMultimap.create();
		String content = null;
		InputStream stream = null;
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
		
		public Http header(String key, String value) {
			headers.put(key, value);
			return this;
		}
		// Last one
		public String header(String key) {
			String value = null;
			for (String v : headers.get(key)) {
				value = v;
			}
			return value;
		}
		
		public Http contentType(String contentType) {
			header(HttpHeaderKey.CONTENT_TYPE, contentType);
			return this;
		}
		public String contentType() {
			return header(HttpHeaderKey.CONTENT_TYPE);
		}
		
		public Http contentLength(long contentLength) {
			header(HttpHeaderKey.CONTENT_LENGTH, String.valueOf(contentLength));
			return this;
		}
		public long contentLength() {
			String v = header(HttpHeaderKey.CONTENT_LENGTH);
			if (v == null) {
				return -1L;
			}
			try {
				return Long.parseLong(v);
			} catch (NumberFormatException nfe) {
				return -1L;
			}
		}
		
		public Http content(String content) {
			this.content = content;
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException e) {
				}
				stream = null;
			}
			async = null;
			return this;
		}
		public String content() {
			return content;
		}
		public Http stream(InputStream stream) {
			content = null;
			async = null;
			this.stream = stream;
			return this;
		}
		public InputStream stream() {
			return stream;
		}
		public Http async(HttpAsync async) {
			content = null;
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException e) {
				}
				stream = null;
			}
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
