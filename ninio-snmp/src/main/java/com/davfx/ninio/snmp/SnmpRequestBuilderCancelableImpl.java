package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Address;

final class SnmpRequestBuilderCancelableImpl implements SnmpRequestBuilder.SnmpRequestBuilderCancelable {
	private final SnmpRequestBuilder that;
	private final Cancelable sender;
	
	public SnmpRequestBuilderCancelableImpl(SnmpRequestBuilder that, Cancelable sender) {
		this.that = that;
		this.sender = sender;
	}

	@Override
	public SnmpRequestBuilder community(String community) {
		return that.community(community);
	}
	@Override
	public SnmpRequestBuilder auth(AuthRemoteSpecification authRemoteSpecification) {
		return that.auth(authRemoteSpecification);
	}
	@Override
	public void cancel() {
		sender.cancel();
	}

	@Override
	public Cancelable receive(SnmpReceiver callback) {
		return that.receive(callback);
	}
	
	@Override
	public SnmpRequestBuilderCancelable build(Address address, Oid oid) {
		return that.build(address, oid);
	}

}
