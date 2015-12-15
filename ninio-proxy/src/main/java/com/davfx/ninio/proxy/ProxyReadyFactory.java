package com.davfx.ninio.proxy;

import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyFactory;

final class ProxyReadyFactory implements ReadyFactory {
	private final Queue queue;
	private final ProxyReadyGenerator proxyReadyGeneratory;
	private final String type;
	
	public ProxyReadyFactory(Queue queue, ProxyReadyGenerator proxyReadyGeneratory, String type) {
		this.queue = queue;
		this.proxyReadyGeneratory = proxyReadyGeneratory;
		this.type = type;
	}

	@Override
	public Ready create() {
		return proxyReadyGeneratory.get(queue, type);
	}
}
