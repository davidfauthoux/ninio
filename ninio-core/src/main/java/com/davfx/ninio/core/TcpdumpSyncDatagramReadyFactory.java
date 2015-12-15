package com.davfx.ninio.core;


public final class TcpdumpSyncDatagramReadyFactory implements ReadyFactory {
	private final Queue queue;
	private final TcpdumpSyncDatagramReady.Receiver receiver;

	public TcpdumpSyncDatagramReadyFactory(Queue queue, TcpdumpSyncDatagramReady.Receiver receiver) {
		this.queue = queue;
		this.receiver = receiver;
	}
	
	@Override
	public Ready create() {
		return new QueueReady(queue, new TcpdumpSyncDatagramReady(receiver));
	}
}
