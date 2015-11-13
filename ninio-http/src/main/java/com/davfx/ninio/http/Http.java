package com.davfx.ninio.http;

import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.core.SocketReadyFactory;
import com.davfx.ninio.core.SslReadyFactory;
import com.davfx.ninio.core.Trust;
import com.davfx.ninio.util.GlobalQueue;

public final class Http {

	private Queue queue = null;
	private ReadyFactory readyFactory = new SocketReadyFactory();
	private ReadyFactory secureReadyFactory = null;

	public Http() {
	}

	public Http withQueue(Queue queue) {
		this.queue = queue;
		return this;
	}
	
	public Http override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}

	public Http overrideSecure(ReadyFactory secureReadyFactory) {
		this.secureReadyFactory = secureReadyFactory;
		return this;
	}

	public Http withTrust(Trust trust) {
		secureReadyFactory = new SslReadyFactory(trust);
		return this;
	}

	public HttpClient client() {
		Queue q = queue;
		if (q == null) {
			q = GlobalQueue.get();
		}
		return new HttpClient(q, readyFactory, secureReadyFactory);
	}
}
