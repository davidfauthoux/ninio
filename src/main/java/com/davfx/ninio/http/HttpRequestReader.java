package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.ByteBufferHandler;
import com.davfx.ninio.common.CloseableByteBufferHandler;

final class HttpRequestReader implements CloseableByteBufferHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestReader.class);

	private final LineReader lineReader = new LineReader();
	private boolean headersRead = false;
	private boolean requestLineRead = false;
	private long contentLength;
	private int countRead = 0;
	private HttpRequest.Method requestMethod;
	private String requestPath;
	private boolean http11;
	private boolean enableGzip = false;
	private final Map<String, String> headers = new HashMap<String, String>();
	private boolean failClose = false;
	private boolean closed = false;
	private final Address address;
	private final boolean secure;

	private final HttpServerHandler handler;
	private final CloseableByteBufferHandler write;
	
	private final Map<String, String> headerSanitization = new HashMap<String, String>();
	
	public HttpRequestReader(Address address, boolean secure, HttpServerHandler handler, CloseableByteBufferHandler write) {
		this.address = address;
		this.secure = secure;
		this.handler = handler;
		this.write = write;

		headerSanitization.put(Http.CONTENT_LENGTH.toLowerCase(), Http.CONTENT_LENGTH);
		headerSanitization.put(Http.CONTENT_ENCODING.toLowerCase(), Http.CONTENT_ENCODING);
		headerSanitization.put(Http.CONTENT_TYPE.toLowerCase(), Http.CONTENT_TYPE);
		headerSanitization.put(Http.ACCEPT_ENCODING.toLowerCase(), Http.ACCEPT_ENCODING);
		headerSanitization.put(Http.TRANSFER_ENCODING.toLowerCase(), Http.TRANSFER_ENCODING);
	}
	
	private void addHeader(String headerLine) throws IOException {
		int i = headerLine.indexOf(Http.HEADER_KEY_VALUE_SEPARATOR);
		if (i < 0) {
			throw new IOException("Invalid header: " + headerLine);
		}
		String key = headerLine.substring(0, i);
		String sanitizedKey = headerSanitization.get(key.toLowerCase());
		if (sanitizedKey != null) {
			key = sanitizedKey;
		}
		String value = headerLine.substring(i + 1).trim();
		headers.put(key, value);
	}
	private void setRequestLine(String requestLine) throws IOException {
		int i = requestLine.indexOf(Http.START_LINE_SEPARATOR);
		if (i < 0) {
			throw new IOException("Invalid request: " + requestLine);
		}
		int j = requestLine.indexOf(Http.START_LINE_SEPARATOR, i + 1);
		if (j < 0) {
			throw new IOException("Invalid request: " + requestLine);
		}
		requestMethod = null;
		String m = requestLine.substring(0, i);
		for (HttpRequest.Method method : HttpRequest.Method.values()) {
			if (method.toString().equals(m)) {
				requestMethod = method;
				break;
			}
		}
		if (requestMethod == null) {
			throw new IOException("Invalid request: " + requestLine);
		}
		requestPath = requestLine.substring(i + 1, j);
		String requestVersion = requestLine.substring(j + 1);
		if (requestVersion.equals(Http.HTTP10)) {
			http11 = false;
		} else if (requestVersion.equals(Http.HTTP11)) {
			http11 = true;
		} else {
			throw new IOException("Unsupported version");
		}
	}
	
	@Override
	public void close() {
		LOGGER.debug("Closing");
		if (failClose) {
			if (!closed) {
				closed = true;
				handler.failed(new IOException("Connection reset by peer"));
			}
		} else {
			if (!closed) {
				closed = true;
				handler.close();
			}
		}
	}
	
	@Override
	public void handle(Address address, ByteBuffer buffer) {
		if (!buffer.hasRemaining()) {
			return;
		}
		try {
			failClose = true;
			
			while (!requestLineRead) {
				// LOGGER.debug("Reading request line");
				String line = lineReader.handle(buffer);
				if (line == null) {
					return;
				}
				setRequestLine(line);
				requestLineRead = true;
			}
	
			while (!headersRead) {
				// LOGGER.debug("Reading header line");
				String line = lineReader.handle(buffer);
				if (line == null) {
					return;
				}
				if (line.isEmpty()) {
					headersRead = true;
					String accept = headers.get(Http.ACCEPT_ENCODING);
					if (accept != null) {
						String[] list = accept.split("\\" + Http.MULTIPLE_SEPARATOR);
						for (String s : list) {
							String[] v = s.trim().split("\\" + Http.EXTENSION_SEPARATOR);
							if (v.length > 0) {
								if (v[0].trim().equalsIgnoreCase(Http.GZIP)) {
									enableGzip = true;
									break;
								}
							}
						}
					}
					handler.handle(new HttpRequest(address, secure, requestMethod, requestPath, headers));
					String contentLengthValue = headers.get(Http.CONTENT_LENGTH);
					if (contentLengthValue != null) {
						try {
							contentLength = Long.parseLong(contentLengthValue);
						} catch (NumberFormatException e) {
							throw new IOException("Invalid Content-Length: " + contentLengthValue);
						}
					} else {
						contentLength = 0;
						handler.ready(new InnerWrite()); // Yes, can be so cool for ws://
					}
				} else {
					// LOGGER.debug("Header line: {}", line);
					addHeader(line);
				}
			}

			if (contentLength == 0) {
				if (buffer.hasRemaining()) {
					handler.handle(null, buffer);
				}
			} else {
				if (countRead < contentLength) {
					if (buffer.hasRemaining()) {
						ByteBuffer d = buffer;
						long toRead = contentLength - countRead;
						if (buffer.remaining() > toRead) {
							d = buffer.duplicate();
							d.limit((int) (buffer.position() + toRead));
							buffer.position((int) (buffer.position() + toRead));
						}
						countRead += d.remaining();
						handler.handle(null, d);
					}
				}
		
				if (countRead == contentLength) {
					failClose = false;
					countRead = 0;
					requestLineRead = false; // another connection possible
					headersRead = false;
					handler.ready(new InnerWrite());
				}
			}
		} catch (IOException e) {
			if (!closed) {
				closed = true;
				write.close();
				handler.failed(e);
			}
		}
	}
	
	private final class InnerWrite implements HttpServerHandler.Write {
		private long countWrite = 0;
		private long writeContentLength = -1;
		private boolean chunked = false;
		private GzipWriter gzipWriter;
		
		public InnerWrite() {
		}
		
		@Override
		public void close() {
			if (gzipWriter != null) {
				gzipWriter.close();
			}
			
			if (http11) {
				if (chunked) {
					write.handle(address, LineReader.toBuffer(Integer.toHexString(0)));
					write.handle(address, LineReader.toBuffer(""));
					return;
				}
				
				if ((writeContentLength >= 0) && (countWrite == writeContentLength)) {
					failClose = false;
					countRead = 0;
					requestLineRead = false; // another connection possible
					headersRead = false;
					return; // keep alive
				}
			}
			
			closed = true;
			
			write.close();
		}
		
		@Override
		public void failed(IOException error) {
			closed = true;
			write.close();
		}

		@Override
		public void write(HttpResponse response) {
			if (http11) {
				if (enableGzip && Http.GZIP.equalsIgnoreCase(response.getHeaders().get(Http.CONTENT_ENCODING))) {
					gzipWriter = new GzipWriter(new ByteBufferHandler() {
						@Override
						public void handle(Address address, ByteBuffer buffer) {
							doWrite(buffer);
						}
					});
					chunked = true;
					// deflated length != source length
				} else {
					chunked = Http.CHUNKED.equalsIgnoreCase(response.getHeaders().get(Http.TRANSFER_ENCODING));
				}
			}
			
			String contentLengthValue = response.getHeaders().get(Http.CONTENT_LENGTH);
			if (contentLengthValue != null) {
				try {
					writeContentLength = Integer.parseInt(contentLengthValue);
				} catch (NumberFormatException e) {
				}
			} else {
				// Don't fallback anymore because of websockets // chunked = true; // Forced
			}
			
			write.handle(null, LineReader.toBuffer((http11 ? Http.HTTP11 : Http.HTTP10) + Http.START_LINE_SEPARATOR + response.getStatus() + Http.START_LINE_SEPARATOR + response.getReason()));
			for (Map.Entry<String, String> h : response.getHeaders().entrySet()) {
				String k = h.getKey();
				String v = h.getValue();
				if (gzipWriter != null) {
					if (k.equals(Http.CONTENT_LENGTH)) {
						continue;
					}
					if (k.equals(Http.TRANSFER_ENCODING)) {
						continue;
					}
				}
				if (!http11) {
					if (k.equals(Http.CONTENT_ENCODING)) {
						continue;
					}
				}
				write.handle(null, LineReader.toBuffer(k + Http.HEADER_KEY_VALUE_SEPARATOR + Http.HEADER_BEFORE_VALUE + v));
			}
			if (chunked) {
				write.handle(null, LineReader.toBuffer(Http.TRANSFER_ENCODING + Http.HEADER_KEY_VALUE_SEPARATOR + Http.HEADER_BEFORE_VALUE + Http.CHUNKED));
			}
			write.handle(null, LineReader.toBuffer(""));
		}
		
		@Override
		public void handle(Address address, ByteBuffer buffer) {
			if (gzipWriter != null) {
				gzipWriter.handle(buffer);
			} else {
				doWrite(buffer);
			}
		}
		
		private void doWrite(ByteBuffer buffer) {
			if (!buffer.hasRemaining()) {
				return;
			}
			if (chunked) {
				write.handle(null, LineReader.toBuffer(Integer.toHexString(buffer.remaining())));
			}
			countWrite += buffer.remaining();
			write.handle(null, buffer);
			if (chunked) {
				write.handle(null, LineReader.toBuffer(""));
			}
		}
	}
	
}
