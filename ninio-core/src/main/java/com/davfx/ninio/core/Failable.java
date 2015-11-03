package com.davfx.ninio.core;

import java.io.IOException;

public interface Failable {
	void failed(IOException e);
}
