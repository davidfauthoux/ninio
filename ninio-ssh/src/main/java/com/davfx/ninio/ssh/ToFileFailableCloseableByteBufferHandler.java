package com.davfx.ninio.ssh;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Failable;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;

public final class ToFileFailableCloseableByteBufferHandler implements FailableCloseableByteBufferHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(ToFileFailableCloseableByteBufferHandler.class);
	
	private final File file;
	private FileChannel channel;
	private final Failable end;
	
	public ToFileFailableCloseableByteBufferHandler(File file, Failable end) throws IOException {
		this.file = file;
		this.end = end;
		
		@SuppressWarnings("resource")
		FileOutputStream out = new FileOutputStream(file);
		channel = out.getChannel();
	}
	
	@Override
	public void failed(IOException e) {
		LOGGER.error("Error reported", e);
		if (channel == null) {
			return;
		}
		fail(e);
	}
	
	private void fail(IOException e) {
		try {
			channel.close();
		} catch (IOException ioe) {
		}
		file.delete();
		channel = null;
		end.failed(e);
	}
	
	@Override
	public void close() {
		if (channel == null) {
			return;
		}
		try {
			channel.close();
		} catch (IOException e) {
			LOGGER.error("Failed to write to: " + file.getAbsolutePath(), e);
			fail(e);
		}
		if (channel == null) {
			return;
		}
		channel = null;
		end.failed(null);
	}
	
	@Override
	public void handle(Address address, ByteBuffer buffer) {
		if (channel == null) {
			return;
		}
		try {
			channel.write(buffer);
		} catch (IOException e) {
			LOGGER.error("Failed to write to: " + file.getAbsolutePath(), e);
			fail(e);
		}
	}
}
