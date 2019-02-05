package com.davfx.ninio.core;

public interface Listening extends ConnectingClosingFailing {
	Connection connecting(Connected connecting);
}