package com.davfx.ninio.core.v4;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

import com.davfx.ninio.core.Disconnectable;

interface Queue extends Disconnectable {
	void execute(Runnable command);
	SelectionKey register(SelectableChannel channel) throws ClosedChannelException;
}
