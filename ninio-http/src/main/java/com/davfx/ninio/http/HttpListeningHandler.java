package com.davfx.ninio.http;

import com.davfx.ninio.core.Buffering;
import com.davfx.ninio.core.Closing;

public interface HttpListeningHandler {
	interface ConnectionHandler extends Closing, Buffering {
		interface ResponseHandler {
			HttpContentSender send(HttpResponse response);
		}
		
		HttpContentReceiver handle(HttpRequest request, ResponseHandler responseHandler);
	}
	
	ConnectionHandler create();
}
