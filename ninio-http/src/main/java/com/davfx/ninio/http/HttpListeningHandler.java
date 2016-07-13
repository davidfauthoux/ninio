package com.davfx.ninio.http;

import java.io.IOException;

public interface HttpListeningHandler {
	interface ResponseHandler {
		HttpContentSender send(HttpResponse response);
	}
	
	HttpContentReceiver handle(HttpRequest request, ResponseHandler responseHandler);

	void closed();
	void failed(IOException ioe);
}
