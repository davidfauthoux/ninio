package com.davfx.ninio.http.v3;

import com.davfx.ninio.core.v3.Failing;

public interface HttpReceiverRequestBuilder {
	HttpReceiverRequestBuilder failing(Failing failing);
	HttpReceiverRequestBuilder receiving(HttpReceiver receiver);
	HttpReceiverRequest build();
}
