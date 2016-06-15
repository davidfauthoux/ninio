package com.davfx.ninio.core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Deprecated
public final class Shared {
	public static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();
	
	private Shared() {
	}
}
