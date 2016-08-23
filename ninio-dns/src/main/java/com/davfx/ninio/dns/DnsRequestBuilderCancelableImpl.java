package com.davfx.ninio.dns;

import java.net.ProtocolFamily;

final class DnsRequestBuilderCancelableImpl implements DnsRequestBuilder.SnmpRequestBuilderCancelable {
	private final DnsRequestBuilder that;
	private final Cancelable sender;
	
	public DnsRequestBuilderCancelableImpl(DnsRequestBuilder that, Cancelable sender) {
		this.that = that;
		this.sender = sender;
	}

	@Override
	public void cancel() {
		sender.cancel();
	}

	@Override
	public Cancelable receive(DnsReceiver callback) {
		return that.receive(callback);
	}
	
	@Override
	public SnmpRequestBuilderCancelable resolve(String host, ProtocolFamily family) {
		return that.resolve(host, family);
	}

}
