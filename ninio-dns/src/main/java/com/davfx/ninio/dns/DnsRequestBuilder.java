package com.davfx.ninio.dns;

import java.net.ProtocolFamily;

public interface DnsRequestBuilder {
	interface SnmpRequestBuilderCancelable extends DnsRequestBuilder, Cancelable {
	}
	
	SnmpRequestBuilderCancelable resolve(String host, ProtocolFamily family);
	Cancelable receive(DnsReceiver callback);
}
