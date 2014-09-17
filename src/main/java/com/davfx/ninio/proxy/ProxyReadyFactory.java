package com.davfx.ninio.proxy;

import com.davfx.ninio.common.ByteBufferAllocator;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyFactory;

final class ProxyReadyFactory implements ReadyFactory {
	private final ProxyReady proxyReady;
	private final String type;
	public ProxyReadyFactory(ProxyReady proxyReady, String type) {
		this.proxyReady = proxyReady;
		this.type = type;
	}

	@Override
	public Ready create(Queue queue, ByteBufferAllocator allocator) {
		return proxyReady.get(queue, type);
	}
}
