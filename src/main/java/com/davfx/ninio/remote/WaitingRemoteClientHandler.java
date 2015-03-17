package com.davfx.ninio.remote;

import java.util.regex.Pattern;

import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Failable;

public interface WaitingRemoteClientHandler extends Closeable, Failable {
	interface Callback extends Closeable {
		interface SendCallback extends Failable {
			void received(String text);
		}
		void send(String line, double timeToResponse, Pattern cut, SendCallback callback);
	}
	void launched(String init, Callback callback);
}
