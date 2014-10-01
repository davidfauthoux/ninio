package com.davfx.ninio.proxy;

import com.davfx.ninio.common.Failable;

public interface ProxyListener extends Failable {
	void connected();
	void disconnected();
}
