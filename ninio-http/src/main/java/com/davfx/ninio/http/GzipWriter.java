package com.davfx.ninio.http;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import com.davfx.ninio.core.Nop;
import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;

final class GzipWriter implements HttpContentSender {
	
	private static final Config CONFIG = ConfigUtils.load(new com.davfx.ninio.http.dependencies.Dependencies(), GzipReader.class);
	private static final int BUFFER_SIZE = CONFIG.getBytes("gzip.buffer").intValue();

	private static final int OS_TYPE_UNKNOWN = 0xFF;

	private boolean gzipHeaderWritten = false;
	private final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
	private final CRC32 crc = new CRC32();

	private final HttpContentSender wrappee;
	
	private boolean finished = false;
	
	public GzipWriter(HttpContentSender wrappee) {
		this.wrappee = wrappee;
	}

	private ByteBuffer buildGzipFooter() {
		ByteBuffer b = ByteBuffer.allocate(8);
		b.order(ByteOrder.LITTLE_ENDIAN);
		b.putInt((int) (crc.getValue() & 0xFFFFFFFFL));
		b.putInt(deflater.getTotalIn());
		b.flip();
		return b;
	}

	private static ByteBuffer buildGzipHeaders() {
		int time = (int) (System.currentTimeMillis() / 1000L);
		ByteBuffer b = ByteBuffer.allocate(10);
		b.order(ByteOrder.LITTLE_ENDIAN);
		b.putShort((short) GzipReader.GZIP_MAGIC);
		b.put((byte) Deflater.DEFLATED);
		b.put((byte) 0);
		b.putInt(time);
		b.put((byte) 0);
		b.put((byte) OS_TYPE_UNKNOWN);
		b.flip();
		return b;
	}

	@Override
	public HttpContentSender send(ByteBuffer buffer, SendCallback callback) {
		if (finished) {
			throw new IllegalStateException();
		}

		deflater.setInput(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
		crc.update(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
		buffer.position(buffer.limit());
		write(callback);
		return this;
	}

	@Override
	public void finish() {
		if (finished) {
			throw new IllegalStateException();
		}

		finished = true;
		deflater.finish();
		write(new Nop());
		wrappee.send(buildGzipFooter(), new Nop());
		wrappee.finish();
	}

	private void write(final SendCallback callback) {
		ByteBuffer toSend = null;
		
		if (!gzipHeaderWritten) {
			toSend = buildGzipHeaders();
			gzipHeaderWritten = true;
		}
		
		while (true) { // !deflater.needsInput()) {
			ByteBuffer deflated = ByteBuffer.allocate(BUFFER_SIZE);
			int c = deflater.deflate(deflated.array(), deflated.arrayOffset() + deflated.position(), deflated.remaining()); //, Deflater.SYNC_FLUSH); //TODO SYNC_FLUSH when HttpSocket used
			if (c <= 0) {
				break;
			}
			
			if (toSend != null) {
				wrappee.send(toSend, new Nop());
			}
			
			deflated.position(deflated.position() + c);
			deflated.flip();
			toSend = deflated;
		}
		
		if (toSend != null) {
			wrappee.send(toSend, callback);
		}
	}
	
	@Override
	public void cancel() {
		finished = true;
		wrappee.cancel();
	}
}
