package com.davfx.ninio.proxy;

import java.io.DataOutputStream;
import java.io.IOException;

public final class EmptyClientSideConfiguration implements ClientSideConfigurator {
	public EmptyClientSideConfiguration() {
	}
	
	@Override
	public void configure(String connecterType, DataOutputStream out) throws IOException {
	}
}