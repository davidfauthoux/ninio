package com.davfx.ninio.http.v3;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.util.Timeout;

public final class TimeoutRequest {
	private TimeoutRequest() {
	}
	
	public static HttpReceiverRequestBuilder wrap(final Timeout t, final double timeout, final HttpReceiverRequestBuilder wrappee) {
		return new HttpReceiverRequestBuilder() {
			private HttpReceiver receiver = null;
			private Failing failing = null;
			
			@Override
			public HttpReceiverRequestBuilder receiving(HttpReceiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
			@Override
			public HttpReceiverRequestBuilder maxRedirections(int maxRedirections) {
				wrappee.maxRedirections(maxRedirections);
				return this;
			}
			
			@Override
			public HttpReceiverRequestBuilder failing(Failing failing) {
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
						final HttpContentReceiver rr = r.received(new Disconnectable() {
							@Override
							public void close() {
								m.cancel();
								disconnectable.close();
							}
						}, response);
						
						m.reset();
						
						return new HttpContentReceiver() {
							@Override
							public void received(ByteBuffer buffer) {
								m.reset();
								rr.received(buffer);
							}
							@Override
							public void ended() {
								m.cancel();
								rr.ended();
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
