package com.davfx.ninio.http;

import java.nio.ByteBuffer;

final class LineReader {
	
	private final StringBuilder line = new StringBuilder();
	private boolean lastCharCR = false;
	
	public LineReader() {
	}
	
	public static ByteBuffer toBuffer(String s) {
		return ByteBuffer.wrap((s + Http.CR + Http.LF).getBytes(Http.USASCII_CHARSET));
	}
	
	public String handle(ByteBuffer buffer) {
		while (true) {
			if (!buffer.hasRemaining()) {
				return null;
			}
			char c = (char) buffer.get(); // ok on charset US-ASCII
			if (lastCharCR) {
				lastCharCR = false;
				if (c == Http.LF) {
					String l = line.toString();
					line.setLength(0);
					return l;
				} else {
					line.append(Http.CR);
				}
			} else if (c == Http.CR) {
				lastCharCR = true;
			} else {
				line.append(c);
			}
		}
	}
}
