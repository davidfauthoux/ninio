package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class HttpResponseReader {
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpResponseReader.class);

	interface RecyclingHandler {
		void recycle();
		void close();
	}
	
	private final LineReader lineReader = new LineReader();
	private boolean headersRead = false;
	private boolean responseLineRead = false;
	private boolean chunked;
	private GzipReader gzipReader;
	private boolean keepAlive;
	private boolean chunkHeaderRead = false;
	private boolean chunkFooterRead = true;
	private long contentLength;
	private int chunkLength;
	private int chunkCountRead = 0;
	private int countRead = 0;
//	private String responseVersion;
	private int responseCode;
	private String responseReason;
	private final Map<String, String> headers = new HashMap<String, String>();
	private boolean failClose = false;
	private boolean closed = false;
	private boolean ended = false;
	private boolean http11;
	
	private final HttpClientHandler handler;
	
	public HttpResponseReader(HttpClientHandler handler) {
		this.handler = handler;
	}
	
	private void addHeader(String headerLine) throws IOException {
		int i = headerLine.indexOf(Http.HEADER_KEY_VALUE_SEPARATOR);
		if (i < 0) {
			throw new IOException("Invalid header: " + headerLine);
		}
		String key = headerLine.substring(0, i);
		String value = headerLine.substring(i + 1).trim();
		headers.put(key, value);
	}
	private void setResponseLine(String responseLine) throws IOException {
		int i = responseLine.indexOf(Http.START_LINE_SEPARATOR);
		if (i < 0) {
			throw new IOException("Invalid response: " + responseLine);
		}
		int j = responseLine.indexOf(Http.START_LINE_SEPARATOR, i + 1);
		if (j < 0) {
			throw new IOException("Invalid response: " + responseLine);
		}
		String responseVersion = responseLine.substring(0, i);
		if (responseVersion.equals(Http.HTTP10)) {
			http11 = false;
		} else if (responseVersion.equals(Http.HTTP11)) {
			http11 = true;
		} else {
			throw new IOException("Unsupported version");
		}
		String code = responseLine.substring(i + 1, j);
		try {
			responseCode = Integer.parseInt(code);
		} catch (NumberFormatException e) {
			throw new IOException("Invalid status code: " + code);
		}
		responseReason = responseLine.substring(j + 1);
	}
	
	public void close() {
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
	
	public void failed(IOException e) {
		if (!closed) {
			closed = true;
			handler.failed(e);
		}
	}
	
	public void handle(ByteBuffer buffer, RecyclingHandler recyclingHandler) {
		if (!buffer.hasRemaining()) {
			return;
		}
		try {
			
			if (ended || closed) {
				throw new IOException("Too much data");
			}
			
			failClose = true;
			while (!responseLineRead) {
				String line = lineReader.handle(buffer);
				if (line == null) {
					return;
				}
				LOGGER.trace("Response line: {}", line);
				setResponseLine(line);
				responseLineRead = true;
			}
			while (!headersRead) {
				String line = lineReader.handle(buffer);
				if (line == null) {
					return;
				}
				if (line.isEmpty()) {
					LOGGER.trace("Header line empty");
					headersRead = true;
					String contentLengthValue = headers.get(Http.CONTENT_LENGTH);
					if (contentLengthValue != null) {
						try {
							contentLength = Long.parseLong(contentLengthValue);
						} catch (NumberFormatException e) {
							throw new IOException("Invalid Content-Length: " + contentLengthValue);
						}
					} else {
						contentLength = -1;
					}
					if (Http.GZIP.equalsIgnoreCase(headers.get(Http.CONTENT_ENCODING))) {
						gzipReader = new GzipReader(handler);
					} else {
						gzipReader = null;
					}
					chunked = Http.CHUNKED.equalsIgnoreCase(headers.get(Http.TRANSFER_ENCODING));
					keepAlive = http11 && Http.KEEP_ALIVE.equalsIgnoreCase(headers.get(Http.CONNECTION));
					handler.received(new HttpResponse(responseCode, responseReason, headers));
				} else {
					LOGGER.trace("Header line: {}", line);
					addHeader(line);
				}
			}
			
			if (chunked) {
				
				while (true) {
				
					while (!chunkFooterRead) {
						String line = lineReader.handle(buffer);
						if (line == null) {
							return;
						}
						if (!line.isEmpty()) {
							throw new IOException("Invalid chunk footer");
						}
						chunkFooterRead = true;
						chunkHeaderRead = false;
						failClose = false;
						if (chunkLength == 0) {
							if (keepAlive) {
								recyclingHandler.recycle();
							} else {
								recyclingHandler.close();
							}
							if (!closed) {
								closed = true;
								ended = true;
								handler.close();
							}
						}
					}

					while (!chunkHeaderRead) {
						String line = lineReader.handle(buffer);
						if (line == null) {
							return;
						}
						failClose = true;
						int i = line.indexOf(Http.EXTENSION_SEPARATOR);
						if (i > 0) { // extensions ignored
							line = line.substring(0, i);
						}
						try {
							chunkLength = Integer.parseInt(line, 16);
						} catch (NumberFormatException e) {
							throw new IOException("Invalid chunk size: " + line);
						}
						chunkHeaderRead = true;
					}
					
					if (!buffer.hasRemaining()) {
						return;
					}
						
					if (chunkHeaderRead && (chunkCountRead < chunkLength)) {
						long totalToRead = contentLength - countRead;
						long toRead = chunkLength - chunkCountRead;
						handleContent(buffer, toRead, totalToRead);
					}
					
					if (chunkCountRead == chunkLength) {
						chunkFooterRead = false;
						chunkCountRead = 0;
					}
				
				}
	
			} else {
			
				if (contentLength >= 0) {
					if (countRead < contentLength) {
						long toRead = contentLength - countRead;
						handleContent(buffer, toRead, toRead);
					}
					if (countRead == contentLength) {
						failClose = false;
						if (keepAlive) {
							recyclingHandler.recycle();
						} else {
							recyclingHandler.close();
						}
						ended = true;
						if (!closed) {
							closed = true;
							handler.close();
						}
					}
				} else {
					failClose = false;
					handleContent(buffer, -1, -1);
				}

			}
			
		} catch (IOException e) {
			if (!closed) {
				closed = true;
				handler.failed(e);
			}
		}
	}
	
    private void handleContent(ByteBuffer buffer, long toRead, long totalRemainingToRead) throws IOException {
    	ByteBuffer deflated = buffer.duplicate(); // We must duplicated this buffer because it can be used later by the user, but must be continued in the handle loop
    	if (toRead >= 0) {
			if (deflated.remaining() > toRead) {
				deflated.limit((int) (buffer.position() + toRead));
			}
    	}
    	
    	chunkCountRead += deflated.remaining();
		countRead += deflated.remaining();
		buffer.position((int) (buffer.position() + deflated.remaining()));

		if (gzipReader == null) {
			handler.handle(null, deflated);
			return;
		}
		
		gzipReader.handle(deflated, totalRemainingToRead);
    }
}
