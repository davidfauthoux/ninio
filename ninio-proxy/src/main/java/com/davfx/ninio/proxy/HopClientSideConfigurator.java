package com.davfx.ninio.proxy;

import java.io.DataOutputStream;
import java.io.IOException;

import com.davfx.ninio.core.Address;

public final class HopClientSideConfigurator implements ClientSideConfigurator {
	private final Address toAddress;
	private final String underlyingType;

	public HopClientSideConfigurator(Address reproxyAddress, String underlyingType) {
		this.toAddress = reproxyAddress;
		this.underlyingType = underlyingType;
	}

	@Override
	public void configure(String connecterType, DataOutputStream out) throws IOException {
		out.writeUTF(toAddress.getHost());
		out.writeInt(toAddress.getPort());
		out.writeUTF(underlyingType);
	}
}
