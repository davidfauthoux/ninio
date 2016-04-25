package com.davfx.ninio.core.v3;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

public interface Queue {
	void execute(Runnable command);
	SelectionKey register(SelectableChannel channel) throws ClosedChannelException;
}
