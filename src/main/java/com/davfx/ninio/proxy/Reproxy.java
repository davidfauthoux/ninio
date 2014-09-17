package com.davfx.ninio.proxy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.ReadyFactory;

public class Reproxy {
	public static final String DEFAULT_TYPE = Reproxy.class.getPackage().getName() + ".reproxy";

	private Reproxy() {
	}

	public static ProxyUtils.ClientSideConfigurator client(Address reproxyAddress, String underlyingType) {
		return new ProxyUtils.ClientSideConfigurator() {
			@Override
			public void configure(String connecterType, DataOutputStream out) throws IOException {
				out.writeUTF(reproxyAddress.getHost());
				out.writeInt(reproxyAddress.getPort());
				out.writeUTF(underlyingType);
			}
		};
	}
	
	public static ProxyUtils.ServerSideConfigurator server() {
		return new ProxyUtils.ServerSideConfigurator() {
			private final Map<Address, ProxyClient> clients = new HashMap<>();
			@Override
			public ReadyFactory configure(String connecterType, DataInputStream in) throws IOException {
				Address proxyAddress = new Address(in.readUTF(), in.readInt());
				ProxyClient c = clients.get(proxyAddress);
				if (c == null) {
					c = new ProxyClient(proxyAddress);
					clients.put(proxyAddress, c);
				}
				String underlyingType = in.readUTF();
				return c.of(underlyingType);
			}
		};
	}
}
