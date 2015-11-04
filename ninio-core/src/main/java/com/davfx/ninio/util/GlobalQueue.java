package com.davfx.ninio.util;

import com.davfx.ninio.core.Queue;

public final class GlobalQueue {
	private GlobalQueue() {
	}
	
	private static Queue QUEUE = null;
	public static synchronized Queue get() {
		if (QUEUE == null) {
			QUEUE = new Queue();
		}
		return QUEUE;
	}
}
