package com.davfx.ninio.ping;

import com.davfx.ninio.core.Failing;

public interface PingReceiver extends Failing {
	void received(double time);
}
