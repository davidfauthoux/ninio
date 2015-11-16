package com.davfx.ninio.telnet;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;

public interface TelnetSharingReadyFactory {
	TelnetReady create(Queue queue, Address address);
}
