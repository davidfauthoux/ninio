package com.davfx.ninio.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LibraryLoader {
	private static final Logger LOGGER = LoggerFactory.getLogger(LibraryLoader.class);

	private LibraryLoader() {
	}
	
	private static final int COPY_BUFFER_SIZE = 10 * 1024;

	private static String convertName(String name) {
		String os = System.getProperty("os.name").toLowerCase();
		if (os.indexOf("nix") >= 0 || os.indexOf("nux") >= 0 || os.indexOf("aix") > 0 ) {
			return "lib" + name + ".so";
		}
		if (os.indexOf("mac") >= 0) {
			return "lib" + name + ".dylib";
		}
		if (os.indexOf("win") >= 0) {
			return name + ".dll";
		}
		throw new UnsatisfiedLinkError("Unknow OS: " + os);
	}
	
	public static void load(ClassLoader classLoader, String path) {
		try {
			int i = path.lastIndexOf('/');
			if (i < 0) {
				path = convertName(path);
			} else {
				path = path.substring(0, i) + "/" + convertName(path.substring(i + 1));
			}
			
			File to;
			
			InputStream in = classLoader.getResourceAsStream(path);
			if (in == null) {
				throw new UnsatisfiedLinkError("Resource not found: " + path);
			}
			try {
				try {
					to = File.createTempFile("lib", ".lib");
					try (OutputStream out = new FileOutputStream(to)) {
						byte[] b = new byte[COPY_BUFFER_SIZE];
						while (true) {
							int l = in.read(b);
							if (l <= 0) {
								break;
							}
							out.write(b, 0, l);
						}
					}
				} finally {
					in.close();
				}
			} catch (IOException ioe) {
				throw new UnsatisfiedLinkError("Library could not be loaded: " + ioe.getMessage());
			}
			
			System.load(to.getAbsolutePath());
		} catch (Throwable ee) {
			LOGGER.error("Could not load library: " + path, ee);
			throw ee;
		}
	}
}
