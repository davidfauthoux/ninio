package com.davfx.ninio.dns;

import com.davfx.ninio.core.Disconnectable;

public interface DnsConnecter extends Disconnectable {
	Cancelable resolve(String host, DnsReceiver callback);
}
