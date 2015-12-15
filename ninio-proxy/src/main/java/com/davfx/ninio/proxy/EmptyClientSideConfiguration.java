package com.davfx.ninio.proxy;

import java.io.DataOutputStream;
import java.io.IOException;

import com.davfx.ninio.core.Address;

public final class EmptyClientSideConfiguration implements ClientSideConfigurator {
	public EmptyClientSideConfiguration() {
	}
	
	@Override
	public void configure(Address address, String connecterType, DataOutputStream out) throws IOException {
	}
}