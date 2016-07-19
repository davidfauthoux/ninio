package com.davfx.ninio.http;

import com.davfx.ninio.core.Disconnectable;

public interface HttpConnecter extends Disconnectable {
	HttpRequestBuilder request();
}
