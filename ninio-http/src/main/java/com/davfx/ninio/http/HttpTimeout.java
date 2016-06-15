package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.Timeout;

public final class HttpTimeout {
	private HttpTimeout() {
	}
	
	public static HttpRequestBuilder wrap(final Timeout t, final double timeout, final HttpRequestBuilder wrappee) {
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

				final Timeout.Manager m = t.set(timeout);

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
						m.reset();
						
						if (r == null) {
							return new HttpContentReceiver() {
								@Override
								public void received(ByteBuffer buffer) {
									m.reset();
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
								m.reset();
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
					public HttpContentSender send(ByteBuffer buffer) {
						m.reset();
						rr.send(buffer);
						return this;
					}
					
					@Override
					public void finish() {
						m.run(new Failing() {
							@Override
							public void failed(IOException e) {
								rr.cancel();
								if (f != null) {
									f.failed(e);
								}
							}
						});
						rr.finish();
					}
					
					@Override
					public void cancel() {
						m.cancel();
						rr.cancel();
					}
				};
			}
		};
	}
}
