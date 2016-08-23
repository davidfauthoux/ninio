package com.davfx.ninio.dns;

import com.davfx.ninio.core.Disconnectable;

public interface DnsConnecter extends Disconnectable {
	void connect(DnsConnection callback);
	DnsRequestBuilder request();
}
