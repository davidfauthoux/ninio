package com.davfx.ninio.proxy;

import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyFactory;

final class ProxyReadyFactory implements ReadyFactory {
	private final ProxyReadyGenerator proxyReadyGeneratory;
	private final String type;
	
	public ProxyReadyFactory(ProxyReadyGenerator proxyReadyGeneratory, String type) {
		this.proxyReadyGeneratory = proxyReadyGeneratory;
		this.type = type;
	}

	@Override
	public Ready create(Queue queue) {
		return proxyReadyGeneratory.get(queue, type);
	}
}
