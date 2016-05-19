package com.davfx.ninio.http.v3;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.v3.Closing;
import com.davfx.ninio.http.HttpResponse;

public interface HttpListeningHandler {
	interface ConnectionHandler extends Closing {
		interface RequestHandler {
			void received(ByteBuffer buffer);
			void ended();
		}
		interface ResponseHandler {
			interface ContentSender {
				ContentSender send(ByteBuffer buffer);
				void finish();
			}

			ContentSender send(HttpResponse response);
		}
		
		RequestHandler handle(HttpRequest request, ResponseHandler responseHandler);
	}
	
	ConnectionHandler create();
}
