package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.core.Timeout;

public final class HttpTimeout {
	private HttpTimeout() {
	}
	
	public static HttpRequestBuilder wrap(final Timeout t, final double timeout, final HttpRequestBuilder wrappee) {
		return new HttpRequestBuilder() {
			@Override
			public HttpRequestBuilder maxRedirections(int maxRedirections) {
				wrappee.maxRedirections(maxRedirections);
				return this;
			}

			private Timeout.Manager m;
			private HttpContentSender sender;

			@Override
			public HttpRequestBuilderHttpContentSender build(HttpRequest request) {
				m = t.set(timeout);
				final HttpContentSender s = wrappee.build(request);

				sender = new HttpContentSender() {
					@Override
					public HttpContentSender send(ByteBuffer buffer, SendCallback callback) {
						m.reset();
						s.send(buffer, callback);
						return this;
					}
					
					@Override
					public void finish() {
						m.run(new Runnable() {
							@Override
							public void run() {
								s.cancel(); // callback.failed will be called by underlying http client
							}
						});
						s.finish();
					}
					
					@Override
					public void cancel() {
						m.cancel();
						s.cancel();
					}
				};
				
				return new HttpRequestBuilderHttpContentSenderImpl(this, sender);
			}
			
			@Override
			public HttpContentSender receive(final HttpReceiver callback) {
				wrappee.receive(new HttpReceiver() {
					@Override
					public void failed(IOException ioe) {
						m.cancel();
						callback.failed(ioe);
					}
					
					@Override
					public HttpContentReceiver received(HttpResponse response) {
						m.reset();
						
						final HttpContentReceiver r = callback.received(response);
						
						return new HttpContentReceiver() {
							@Override
							public void received(ByteBuffer buffer) {
								m.reset();
								if (r != null) {
									r.received(buffer);
								}
							}
							@Override
							public void ended() {
								m.cancel();
								if (r != null) {
									r.ended();
								}
							}
						};
					}
				});
				return sender;
			}
		};
	}
}
