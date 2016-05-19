package com.davfx.ninio.http.v3;

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
	private static final int BUFFER_SIZE = CONFIG.getBytes("ninio.http.gzip.buffer").intValue();

	static final int GZIP_MAGIC = 0x8b1f;
	
//	private static final int FTEXT = 1; // Extra text
	private static final int FHCRC = 2; // Header CRC
	private static final int FEXTRA = 4; // Extra field
	private static final int FNAME = 8; // File name
	private static final int FCOMMENT = 16; // File comment

	private final Inflater inflater = new Inflater(true);
    private final CRC32 crc = new CRC32();
    private final ByteBuffer header = ByteBuffer.allocate(10);
    private final ByteBuffer extraCountBuffer = ByteBuffer.allocate(2);
    private boolean headerRead = false;
    private boolean extraCountRead = false;
    private int extraCount;
    private int extraSkipped = 0;
    private boolean nameSkipped = false;
    private boolean commentSkipped = false;
    private int headerCrcSkipped = 2;

    private final ByteBuffer previewFooter = ByteBuffer.allocate(8);
    
	public GzipReader() {
	}
	
	public void handle(ByteBuffer deflated, ByteBufferHandler handler) throws IOException {
		if ((previewFooter.position() + deflated.remaining()) > previewFooter.capacity()) {
			previewFooter.flip();
			read(previewFooter, handler);
			previewFooter.rewind();
		}
		
		if (deflated.remaining() < (previewFooter.capacity() - previewFooter.position())) {
			previewFooter.put(deflated);
			return;
		}
		
		ByteBuffer deflatedKeepingFooter = deflated.duplicate();
		deflatedKeepingFooter.limit(deflatedKeepingFooter.limit() - previewFooter.position());
		read(deflatedKeepingFooter, handler);
		ByteBuffer footer = deflated.duplicate();
		footer.position(footer.limit() - previewFooter.position());
		previewFooter.put(footer);
	}
	
    private void read(ByteBuffer deflated, ByteBufferHandler handler) throws IOException {
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

		if (deflated.hasRemaining()) {
			inflater.setInput(deflated.array(), deflated.position(), deflated.remaining());
			deflated.position(deflated.position() + deflated.remaining());
			
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
    }
    
    public void close(ByteBufferHandler handler) throws IOException {
    	if (previewFooter.position() < previewFooter.capacity()) {
			throw new IOException("Footer too short");
    	}
    	previewFooter.flip();
		previewFooter.order(ByteOrder.LITTLE_ENDIAN);
		if ((previewFooter.getInt() & 0xFFFFFFFFL) != crc.getValue()) {
			throw new IOException("Bad CRC");
		}
		if ((previewFooter.getInt() & 0xFFFFFFFFL) != inflater.getBytesWritten()) {
			throw new IOException("Bad length");
		}
	}
}
