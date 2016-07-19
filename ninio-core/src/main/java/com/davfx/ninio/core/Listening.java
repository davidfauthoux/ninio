package com.davfx.ninio.core;

public interface Listening extends Connecting, Failing, Closing {
	Connection connecting(Connected connecting);
}