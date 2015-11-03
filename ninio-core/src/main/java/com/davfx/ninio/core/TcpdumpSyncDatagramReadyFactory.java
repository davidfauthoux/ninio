package com.davfx.ninio.core;


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
