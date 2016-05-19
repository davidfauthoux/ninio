package com.davfx.ninio.http.v3;

import com.davfx.ninio.core.v3.Closing;

public interface HttpListeningHandler {
	interface ConnectionHandler extends Closing {
		interface ResponseHandler {
			HttpContentSender send(HttpResponse response);
		}
		
		HttpContentReceiver handle(HttpRequest request, ResponseHandler responseHandler);
	}
	
	ConnectionHandler create();
}
