package com.davfx.ninio.common;

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
