package com.davfx.ninio.telnet;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;

public interface TelnetSharingReadyFactory {
	String eol();
	TelnetReady create(Queue queue, ReadyFactory readyFactory, Address address);
}
