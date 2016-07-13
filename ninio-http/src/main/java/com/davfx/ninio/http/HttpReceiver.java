package com.davfx.ninio.http;

import java.io.IOException;

public interface HttpReceiver {
	HttpContentReceiver received(HttpResponse response);
	void failed(IOException ioe);
}
