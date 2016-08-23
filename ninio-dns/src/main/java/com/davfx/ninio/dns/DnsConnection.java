package com.davfx.ninio.dns;

import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Failing;

public interface DnsConnection extends Connecting, Failing, Closing {
}
