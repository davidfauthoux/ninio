package com.davfx.ninio.util;

import java.io.File;

public final class DeepDelete {
	private DeepDelete() {
	}
	
	// Use with caution!
	public static void deleteCompletely(File fileOrDirectory) {
		if (fileOrDirectory.isDirectory()) {
			File[] f = fileOrDirectory.listFiles();
			if (f == null) {
				return;
			}
			for (File g : f) {
				deleteCompletely(g);
			}
			fileOrDirectory.delete();
		} else {
			fileOrDirectory.delete();
		}
	}
}
