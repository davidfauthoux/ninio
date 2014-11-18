package com.davfx.ninio.proxy.sync;

import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.QueueReady;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyFactory;

public final class TcpdumpSyncDatagramReadyFactory implements ReadyFactory {
	private final TcpdumpSyncDatagramReady.Receiver receiver;

	public TcpdumpSyncDatagramReadyFactory(TcpdumpSyncDatagramReady.Receiver receiver) {
		this.receiver = receiver;
	}
	
	@Override
	public Ready create(Queue queue) {
		return new QueueReady(queue, new TcpdumpSyncDatagramReady(receiver));
	}
}
