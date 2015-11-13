package com.davfx.ninio.http;

import java.nio.ByteBuffer;

import com.google.common.base.Charsets;

final class LineReader {
	
	private final StringBuilder line = new StringBuilder();
	private boolean lastCharCR = false;
	
	public LineReader() {
	}
	
	public static ByteBuffer toBuffer(String s) {
		return ByteBuffer.wrap((s + HttpSpecification.CR + HttpSpecification.LF).getBytes(Charsets.US_ASCII));
	}
	
	public String handle(ByteBuffer buffer) {
		while (true) {
			if (!buffer.hasRemaining()) {
				return null;
			}
			char c = (char) buffer.get(); // ok on charset US-ASCII
			if (lastCharCR) {
				lastCharCR = false;
				if (c == HttpSpecification.LF) {
					String l = line.toString();
					line.setLength(0);
					return l;
				} else {
					line.append(HttpSpecification.CR);
				}
			} else if (c == HttpSpecification.CR) {
				lastCharCR = true;
			} else {
				line.append(c);
			}
		}
	}
}
