package com.davfx.ninio.ftp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Failable;

public final class ToFileDownloadCallback implements FtpClientHandler.Callback.DownloadCallback {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ToFileDownloadCallback.class);
	
	private final Failable end;
	private final File file;
	private IOException error = null;
	private OutputStream out = null;
	
	public ToFileDownloadCallback(File file, Failable end) {
		this.file = file;
		this.end = end;
	}
	
	@Override
	public void doesNotExist(String path) {
		LOGGER.debug("Does not exist: {}", path);
		end.failed(new IOException("Does not exist: " + path));
	}
	
	@Override
	public void failed(IOException e) {
		end.failed(e);
	}
	
	@Override
	public void close() {
		if (out != null) {
			try {
				out.close();
			} catch (IOException ioe) {
				LOGGER.error("File could not be properly closed: {}", file, ioe);
				error = ioe;
			}
			out = null;
		}
		end.failed(error);
	}
	
	@Override
	public void handle(Address address, ByteBuffer buffer) {
		System.out.println(buffer.remaining());
		if (error != null) {
			return;
		}
		if (out == null) {
			try {
				out = new FileOutputStream(file);
			} catch (FileNotFoundException ioe) {
				LOGGER.error("File could not be created: {}", file, ioe);
				error = ioe;
				out = null;
			}
		}
		if (out != null) {
			try {
				out.write(buffer.array(), buffer.position(), buffer.remaining());
			} catch (IOException ioe) {
				LOGGER.error("Could not write to: {}", file, ioe);
				error = ioe;
				try {
					out.close();
				} catch (IOException e) {
				}
				out = null;
			}
		}
		
	}
}
