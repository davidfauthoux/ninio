package com.davfx.ninio.http.v3;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.Limit;

public final class HttpLimit {
	private HttpLimit() {
	}
	
	public static HttpRequestBuilder wrap(final Limit l, final HttpRequestBuilder wrappee) {
		return new HttpRequestBuilder() {
			private HttpReceiver receiver = null;
			private Failing failing = null;
			
			@Override
			public HttpRequestBuilder receiving(HttpReceiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
			@Override
			public HttpRequestBuilder maxRedirections(int maxRedirections) {
				wrappee.maxRedirections(maxRedirections);
				return this;
			}
			
			@Override
			public HttpRequestBuilder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public HttpContentSender build(HttpRequest request) {
				final Failing f = failing;
				final HttpReceiver r = receiver;

				final Limit.Manager m = l.inc();

				wrappee.failing(new Failing() {
					@Override
					public void failed(IOException e) {
						m.cancel();
						if (f != null) {
							f.failed(e);
						}
					}
				});
				
				wrappee.receiving(new HttpReceiver() {
					@Override
					public HttpContentReceiver received(final Disconnectable disconnectable, HttpResponse response) {
						if (r == null) {
							return new HttpContentReceiver() {
								@Override
								public void received(ByteBuffer buffer) {
								}
								@Override
								public void ended() {
									m.cancel();
								}
							};
						}
						
						final HttpContentReceiver rr = r.received(new Disconnectable() {
							@Override
							public void close() {
								m.cancel();
								disconnectable.close();
							}
						}, response);
						
						return new HttpContentReceiver() {
							@Override
							public void received(ByteBuffer buffer) {
								if (rr != null) {
									rr.received(buffer);
								}
							}
							@Override
							public void ended() {
								m.cancel();
								if (rr != null) {
									rr.ended();
								}
							}
						};
					}
				});

				final HttpContentSender rr = wrappee.build(request);
				
				return new HttpContentSender() {
					@Override
					public HttpContentSender send(final ByteBuffer buffer) {
						m.add(new Runnable() {
							@Override
							public void run() {
								rr.send(buffer);
							}
						});
						return this;
					}
					
					@Override
					public void finish() {
						m.add(new Runnable() {
							@Override
							public void run() {
								rr.finish();
							}
						});
					}
					
					@Override
					public void cancel() {
						m.add(new Runnable() {
							@Override
							public void run() {
								rr.cancel();
							}
						});
						m.cancel();
					}
				};
			}
		};
	}
}
