package com.davfx.ninio.http.v3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Deque;
import java.util.LinkedList;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.davfx.ninio.core.v3.Failing;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

final class GzipReader implements HttpContentReceiver, Failing {

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

    private static final int FOOTER_LENGTH = 8;
    
    private final Deque<ByteBuffer> previewFooter = new LinkedList<>();
    private int currentPreviewFooterLength = 0;

	private final HttpContentReceiver wrappee;
	private final Failing failing;
	
	private boolean ended = false;

	public GzipReader(Failing failing, HttpContentReceiver wrappee) {
		this.failing = failing;
		this.wrappee = wrappee;
	}
	
	// MUST sync-ly consume buffer
	@Override
	public void received(ByteBuffer deflated) {
		if (ended) {
			throw new IllegalStateException();
		}

		if (deflated.remaining() >= FOOTER_LENGTH) {
			for (ByteBuffer b : previewFooter) {
				if (!read(b)) {
					return;
				}
			}
			previewFooter.clear();
			currentPreviewFooterLength = 0;
			
			ByteBuffer deflatedKeepingFooter = deflated.duplicate();
			deflatedKeepingFooter.limit(deflatedKeepingFooter.limit() - FOOTER_LENGTH);
			deflated.position(deflated.position() + deflatedKeepingFooter.remaining());
			if (!read(deflatedKeepingFooter)) {
				return;
			}

			currentPreviewFooterLength += deflated.remaining();
			previewFooter.addLast(deflated.duplicate());
			deflated.position(deflated.position() + deflated.remaining());
			return;
		} else {
			currentPreviewFooterLength += deflated.remaining();
			previewFooter.addLast(deflated.duplicate());
			deflated.position(deflated.position() + deflated.remaining());

			int toFlush = FOOTER_LENGTH - currentPreviewFooterLength;
			while (toFlush > 0) {
				ByteBuffer b = previewFooter.getFirst();

				ByteBuffer d = b.duplicate();
				d.limit(Math.min(d.limit(), toFlush));
				b.position(b.position() + d.remaining());
				toFlush -= d.remaining();
				if (!read(d)) {
					return;
				}
				if (!b.hasRemaining()) {
					previewFooter.removeFirst();
				}
			}
		}
	}
	
    private boolean read(ByteBuffer deflated) {
		if (!headerRead) {
			while (header.hasRemaining()) {
				if (!deflated.hasRemaining()) {
					return true;
				}
				header.put(deflated.get());
			}
			header.flip();
			header.order(ByteOrder.LITTLE_ENDIAN);
			if ((header.getShort() & 0xFFFF) != GZIP_MAGIC) {
				ended = true;
				failing.failed(new IOException("Invalid Gzip magic number"));
				return false;
			}
			if ((header.get() & 0xFF) != Deflater.DEFLATED) {
				ended = true;
				failing.failed(new IOException("Invalid Gzip method"));
				return false;
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
					return true;
				}
				extraCountBuffer.put(deflated.get());
			}
			extraCountBuffer.flip();
			extraCountBuffer.order(ByteOrder.LITTLE_ENDIAN);
			extraCount = header.getShort() & 0xFFFF;
		}
		while (extraSkipped < extraCount) {
			if (!deflated.hasRemaining()) {
				return true;
			}
			deflated.get();
			extraSkipped++;
		}
		while (!nameSkipped) {
			if (!deflated.hasRemaining()) {
				return true;
			}
			if (deflated.get() == 0) {
				nameSkipped = true;
			}
		}
		while (!commentSkipped) {
			if (!deflated.hasRemaining()) {
				return true;
			}
			if (deflated.get() == 0) {
				commentSkipped = true;
			}
		}
		while (headerCrcSkipped < 2) {
			if (!deflated.hasRemaining()) {
				return true;
			}
			deflated.get();
			headerCrcSkipped++;
		}

		if (deflated.hasRemaining()) {
			inflater.setInput(deflated.array(), deflated.arrayOffset() + deflated.position(), deflated.remaining());
			deflated.position(deflated.position() + deflated.remaining());
			
			while (true) { //!inflater.needsInput() && !inflater.finished()) {
				ByteBuffer inflated = ByteBuffer.allocate(BUFFER_SIZE);
				try {
					int c = inflater.inflate(inflated.array(), inflated.arrayOffset() + inflated.position(), inflated.remaining());
					if (c == 0) {
						break;
					}
					crc.update(inflated.array(), inflated.arrayOffset() + inflated.position(), c);
					inflated.position(inflated.position() + c);
				} catch (DataFormatException e) {
					ended = true;
					failing.failed(new IOException("Could not inflate", e));
					return false;
				}
				inflated.flip();
				wrappee.received(inflated);
			}
		}
		
		return true;
    }
    
    @Override
    public void ended() {
		if (ended) {
			throw new IllegalStateException();
		}
    	if (currentPreviewFooterLength < FOOTER_LENGTH) {
    		ended = true;
    		failing.failed(new IOException("Footer too short, missing " + (FOOTER_LENGTH - currentPreviewFooterLength) + " bytes"));
    		return;
    	}
    	
    	ByteBuffer b = ByteBuffer.allocate(FOOTER_LENGTH);
    	for (ByteBuffer d : previewFooter) {
    		b.put(d);
    	}
    	b.flip();
		b.order(ByteOrder.LITTLE_ENDIAN);
		if ((b.getInt() & 0xFFFFFFFFL) != crc.getValue()) {
			ended = true;
			failing.failed(new IOException("Bad CRC"));
    		return;
		}
		if ((b.getInt() & 0xFFFFFFFFL) != inflater.getBytesWritten()) {
			ended = true;
			failing.failed(new IOException("Bad length"));
    		return;
		}
		ended = true;
		wrappee.ended();
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
