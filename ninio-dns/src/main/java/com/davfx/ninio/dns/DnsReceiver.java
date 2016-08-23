package com.davfx.ninio.dns;

import com.davfx.ninio.core.Failing;

public interface DnsReceiver extends Failing {
	void received(byte[] ip);
}
