package com.davfx.ninio.core.v4;

import java.util.concurrent.CompletableFuture;

import com.davfx.ninio.core.Address;

public interface Connection extends Disconnectable {
	CompletableFuture<Void> onClose();
	CompletableFuture<Void> connect(Address connectAddress);
	CompletableFuture<Void> read(MutableByteArray buffer);
	CompletableFuture<Void> write(ByteArray buffer);
	void close();
}
