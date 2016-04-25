package com.davfx.ninio.core.v3;

public interface NinioBuilder<T> {
	T create(Queue queue);
}
