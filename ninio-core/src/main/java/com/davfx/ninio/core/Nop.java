package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class Nop implements Connection, SendCallback {
	public Nop() {
	}
	
	@Override
	public void connected(Address address) {
	}
	@Override
	public void closed() {
	}
	@Override
	public void failed(IOException ioe) {
	}
	@Override
	public void received(Address address, ByteBuffer buffer) {
	}
	
	@Override
	public void sent() {
	}
}
