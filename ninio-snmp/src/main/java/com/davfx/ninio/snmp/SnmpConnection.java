package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Failing;

public interface SnmpConnection extends Connecting, Failing, Closing {
}
