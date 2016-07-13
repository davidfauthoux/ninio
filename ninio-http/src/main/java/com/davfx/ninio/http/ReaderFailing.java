package com.davfx.ninio.http;

import java.io.IOException;

interface ReaderFailing {
	void failed(IOException ioe);
}
