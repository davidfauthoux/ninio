package com.davfx.ninio.core.v3;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class ExecutorUtils {
	private ExecutorUtils() {
	}
	
	public static void shutdown(ExecutorService executor) {
		executor.shutdown();
		try {
			executor.awaitTermination(0, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
		}
	}
}
