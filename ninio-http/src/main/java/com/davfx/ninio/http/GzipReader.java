package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.davfx.ninio.core.ByteBufferHandler;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

final class GzipReader {

	private static final Config CONFIG = ConfigFactory.load(GzipReader.class.getClassLoader());
	private static final int BUFFER_SIZE = CONFIG.getBytes("ninio.http.gzip.buffer.size").intValue();

	static final int GZIP_MAGIC = 0x8b1f;
	
//	private static final int FTEXT = 1; // Extra text
	private static final int FHCRC = 2; // Header CRC
	private static final int FEXTRA = 4; // Extra field
	private static final int FNAME = 8; // File name
	private static final int FCOMMENT = 16; // File comment

	private final Inflater inflater = new Inflater(true);
    private final CRC32 crc = new CRC32();
    private final ByteBuffer header = ByteBuffer.allocate(10);
    private final ByteBuffer footer = ByteBuffer.allocate(8);
    private final ByteBuffer extraCountBuffer = ByteBuffer.allocate(2);
    private boolean headerRead = false;
    private boolean footerRead = false;
    private boolean extraCountRead = false;
    private int extraCount;
    private int extraSkipped = 0;
    private boolean nameSkipped = false;
    private boolean commentSkipped = false;
    private int headerCrcSkipped = 2;

    private final ByteBufferHandler handler;
    
	public GzipReader(ByteBufferHandler handler) {
		this.handler = handler;
	}
	
    public void handle(ByteBuffer deflated, long totalRemainingToRead) throws IOException {
		int remaining = deflated.remaining();
		
		if (!headerRead) {
			while (header.hasRemaining()) {
				if (!deflated.hasRemaining()) {
					return;
				}
				header.put(deflated.get());
			}
			header.flip();
			header.order(ByteOrder.LITTLE_ENDIAN);
			if ((header.getShort() & 0xFFFF) != GZIP_MAGIC) {
				throw new IOException("Invalid Gzip magic number");
			}
			if ((header.get() & 0xFF) != Deflater.DEFLATED) {
				throw new IOException("Invalid Gzip method");
			}
			int flags = header.get() & 0xFF;
			if ((flags & FEXTRA) == 0) {
				extraCountRead = true;
				extraCount = 0;
			}
			// Skip optional file name
			if ((flags & FNAME) == 0) {
				nameSkipped = true;
			}
			// Skip optional file comment
			if ((flags & FCOMMENT) == 0) {
				commentSkipped = true;
			}
			// Check optional header CRC
			if ((flags & FHCRC) == 0) {
				headerCrcSkipped = 2;
			}
			// 6 bytes skipped
			headerRead = true;
		}
		if (!extraCountRead) {
			while (extraCountBuffer.hasRemaining()) {
				if (!deflated.hasRemaining()) {
					return;
				}
				extraCountBuffer.put(deflated.get());
			}
			extraCountBuffer.flip();
			extraCountBuffer.order(ByteOrder.LITTLE_ENDIAN);
			extraCount = header.getShort() & 0xFFFF;
		}
		while (extraSkipped < extraCount) {
			if (!deflated.hasRemaining()) {
				return;
			}
			deflated.get();
			extraSkipped++;
		}
		while (!nameSkipped) {
			if (!deflated.hasRemaining()) {
				return;
			}
			if (deflated.get() == 0) {
				nameSkipped = true;
			}
		}
		while (!commentSkipped) {
			if (!deflated.hasRemaining()) {
				return;
			}
			if (deflated.get() == 0) {
				commentSkipped = true;
			}
		}
		while (headerCrcSkipped < 2) {
			if (!deflated.hasRemaining()) {
				return;
			}
			deflated.get();
			headerCrcSkipped++;
		}

		int r = deflated.remaining();
		if (totalRemainingToRead >= 0) {
			totalRemainingToRead -= remaining - deflated.remaining();
			if (deflated.remaining() > (totalRemainingToRead - 8)) {
				r = (int) (totalRemainingToRead - 8);
			}
		}
		
		if (r > 0) {
			inflater.setInput(deflated.array(), deflated.position(), r);
			deflated.position(deflated.position() + r);
			if (totalRemainingToRead >= 0) {
				totalRemainingToRead -= r;
			}
			
			while (true) { //!inflater.needsInput() && !inflater.finished()) {
				ByteBuffer inflated = ByteBuffer.allocate(BUFFER_SIZE);
				try {
					int c = inflater.inflate(inflated.array(), inflated.position(), inflated.remaining());
					if (c == 0) {
						break;
					}
					crc.update(inflated.array(), inflated.position(), c);
					inflated.position(inflated.position() + c);
				} catch (DataFormatException e) {
					throw new IOException("Could not inflate", e);
				}
				inflated.flip();
				handler.handle(null, inflated);
			}
		}
		
		if (totalRemainingToRead >= 0) {
			if (totalRemainingToRead <= 8) {
				if (!footerRead) {
					while (footer.hasRemaining()) {
						if (!deflated.hasRemaining()) {
							return;
						}
						footer.put(deflated.get());
					}
					footer.flip();
					footer.order(ByteOrder.LITTLE_ENDIAN);
					if ((footer.getInt() & 0xFFFFFFFFL) != crc.getValue()) {
						throw new IOException("Bad CRC");
					}
					if ((footer.getInt() & 0xFFFFFFFFL) != inflater.getBytesWritten()) {
						throw new IOException("Bad length");
					}
					footerRead = true;
				}
			}
		}
	}
}
