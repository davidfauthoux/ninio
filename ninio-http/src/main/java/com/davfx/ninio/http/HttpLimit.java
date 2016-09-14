package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Limit;
import com.davfx.ninio.core.SendCallback;

public final class HttpLimit {
	private HttpLimit() {
	}
	
	public static HttpRequestBuilder wrap(final Limit l, final int max, final HttpRequestBuilder wrappee) {
		return new HttpRequestBuilder() {
			@Override
			public HttpRequestBuilder maxRedirections(int maxRedirections) {
				wrappee.maxRedirections(maxRedirections);
				return this;
			}

			private Limit.Manager m;
			private HttpContentSender sender;

			@Override
			public HttpRequestBuilderHttpContentSender build(HttpRequest request) {
				m = l.inc(max);
				final HttpContentSender s = wrappee.build(request);

				sender = new HttpContentSender() {
					@Override
					public HttpContentSender send(final ByteBuffer buffer, final SendCallback callback) {
						m.add(new Runnable() {
							@Override
							public void run() {
								s.send(buffer, callback);
							}
						});
						return this;
					}
					
					@Override
					public void finish() {
						m.add(new Runnable() {
							@Override
							public void run() {
								s.finish();
							}
						});
					}
					
					@Override
					public void cancel() {
						m.add(new Runnable() {
							@Override
							public void run() {
								s.cancel();
							}
						});
						m.cancel();
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
						final HttpContentReceiver r = callback.received(response);
						
						return new HttpContentReceiver() {
							@Override
							public void received(ByteBuffer buffer) {
								r.received(buffer);
							}
							@Override
							public void ended() {
								m.cancel();
								r.ended();
							}
						};
					}
				});
				return sender;
			}
		};
	}
}
