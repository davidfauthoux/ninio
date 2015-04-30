package com.davfx.ninio.proxy.sync;

import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.QueueReady;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyFactory;

@Deprecated
public final class SyncDatagramReadyFactory implements ReadyFactory {
	private final SyncDatagramReady.Receiver receiver;

	public SyncDatagramReadyFactory(SyncDatagramReady.Receiver receiver) {
		this.receiver = receiver;
	}
	
	@Override
	public Ready create(Queue queue) {
		return new QueueReady(queue, new SyncDatagramReady(receiver));
	}
}
