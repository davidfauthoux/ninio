package com.davfx.ninio.common;

import java.io.IOException;

public interface Failable {
	void failed(IOException e);
}
