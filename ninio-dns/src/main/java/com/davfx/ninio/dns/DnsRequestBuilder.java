package com.davfx.ninio.dns;

import java.net.ProtocolFamily;

public interface DnsRequestBuilder extends Cancelable {
	DnsRequestBuilder resolve(String host, ProtocolFamily family);
	Cancelable receive(DnsReceiver callback);
}
