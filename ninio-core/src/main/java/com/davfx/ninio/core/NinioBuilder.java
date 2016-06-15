package com.davfx.ninio.core;

public interface NinioBuilder<T> {
	T create(Queue queue);
}
