package com.davfx.ninio.core;

import java.util.concurrent.Executor;

public interface NinioProvider {
	Queue queue(long id);
	Executor executor();
}
