package com.davfx.ninio.http.v3;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.v3.Failing;

final class ChunkedReader implements HttpContentReceiver, Failing {

	private final HttpContentReceiver wrappee;
	private final Failing failing;
	
	private final LineReader lineReader = new LineReader();
	private boolean chunkHeaderRead = false;
	private int chunkLength;
	private int chunkCountRead;

	private boolean ended = false;
	
	public ChunkedReader(Failing failing, HttpContentReceiver wrappee) {
		this.failing = failing;
		this.wrappee = wrappee;
	}
	
	@Override
	public void received(ByteBuffer buffer) {
		if (ended) {
			throw new IllegalStateException();
		}
		while (true) {
			
			while (!chunkHeaderRead) {
				String line = lineReader.handle(buffer);
				if (line == null) {
					return;
				}
				try {
					chunkLength = Integer.parseInt(line, 16);
				} catch (NumberFormatException e) {
					ended = true;
					failing.failed(new IOException("Invalid chunk size: " + line));
					return;
				}
				chunkHeaderRead = true;
				chunkCountRead = 0;
			}

			if (chunkHeaderRead) {
				if (chunkCountRead < chunkLength) {
					if (!buffer.hasRemaining()) {
						return;
					}

					long toRead = chunkLength - chunkCountRead;
					
			    	ByteBuffer d = buffer.duplicate();
			    	if (toRead >= 0) {
						if (d.remaining() > toRead) {
							d.limit((int) (buffer.position() + toRead));
						}
			    	}
			    	
			    	chunkCountRead += d.remaining();
					buffer.position((int) (buffer.position() + d.remaining()));

					wrappee.received(d);
				}
					
				if (chunkCountRead == chunkLength) {
					while (chunkHeaderRead) {
						String line = lineReader.handle(buffer);
						if (line == null) {
							return;
						}
						if (!line.isEmpty()) {
							ended = true;
							failing.failed(new IOException("Invalid chunk footer"));
							return;
						}
						chunkHeaderRead = false;
					}

					if (chunkLength == 0) {
						ended = true;
						wrappee.ended();
						return;
					}
				}
			}
		
		}
	}
	
	@Override
	public void ended() {
		if (ended) {
			throw new IllegalStateException();
		}
		ended = true;
		failing.failed(new IOException("Connection closed prematurely"));
	}
	
	@Override
	public void failed(IOException e) {
		if (ended) {
			throw new IllegalStateException();
		}
		ended = true;
		failing.failed(e);
	}
}
