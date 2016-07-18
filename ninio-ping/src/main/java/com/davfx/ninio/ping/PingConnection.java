package com.davfx.ninio.ping;

import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Failing;

public interface PingConnection extends Connecting, Failing, Closing {
}
