package com.davfx.ninio.http.util;

import java.io.IOException;
import java.io.OutputStream;

import com.davfx.ninio.http.HttpContentType;
import com.davfx.ninio.http.HttpMessage;
import com.davfx.ninio.http.HttpStatus;

public interface HttpController {
	interface HttpStream {
		interface OutputStreamFactory {
			OutputStream open() throws IOException;
		}
		void produce(OutputStreamFactory output) throws Exception;
	}
	final class Http {
		final int status;
		final String reason;
		String contentType = HttpContentType.plainText();
		String content = null;
		HttpStream stream = null;
		
		Http(int status, String reason) {
			this.status = status;
			this.reason = reason;
		}

		public Http contentType(String contentType) {
			this.contentType = contentType;
			return this;
		}
		
		public Http content(String content) {
			this.content = content;
			stream = null;
			return this;
		}
		public Http stream(HttpStream stream) {
			content = null;
			this.stream = stream;
			return this;
		}
		
		public static Http ok() {
			return new Http(HttpStatus.OK, HttpMessage.OK);
		}
		public static Http internalServerError() {
			return new Http(HttpStatus.INTERNAL_SERVER_ERROR, HttpMessage.INTERNAL_SERVER_ERROR);
		}
	}
}
