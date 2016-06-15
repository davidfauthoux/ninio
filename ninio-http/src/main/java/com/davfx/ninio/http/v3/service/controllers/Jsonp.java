package com.davfx.ninio.http.v3.service.controllers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;

import com.davfx.ninio.http.v3.HttpMethod;
import com.davfx.ninio.http.v3.service.HttpController;
import com.davfx.ninio.http.v3.service.annotations.QueryParameter;
import com.davfx.ninio.http.v3.service.annotations.Route;
import com.google.common.base.Charsets;

public final class Jsonp implements HttpController {
	public Jsonp() {
	}
	
	private static HttpController.Http wrap(final String jsonp) {
		if (jsonp == null) {
			return null;
		}

		final byte[] jsonpHeaderAsBytes = (jsonp + "(").getBytes(Charsets.UTF_8);
		final byte[] jsonpFooterAsBytes = ");".getBytes(Charsets.UTF_8);

		return Http.wrap(new HttpWrap() {
			@Override
			public void handle(Http http) throws Exception {
				final HttpAsync async = http.async();
				if (async != null) {
					http.async(new HttpAsync() {
						@Override
						public void produce(final HttpAsyncOutput output) {
							async.produce(new HttpAsyncOutput() {
								private boolean written = false;
								private void writeJsonpHeader() {
									if (written) {
										return;
									}
									output.produce(ByteBuffer.wrap(jsonpHeaderAsBytes));
									written = true;
								}
								
								@Override
								public void finish() {
									output.produce(ByteBuffer.wrap(jsonpFooterAsBytes));
									output.finish();
								}
								@Override
								public void cancel() {
									output.cancel();
								}
								@Override
								public HttpAsyncOutput produce(String buffer) {
									writeJsonpHeader();
									output.produce(buffer);
									return this;
								}
								@Override
								public HttpAsyncOutput produce(ByteBuffer buffer) {
									writeJsonpHeader();
									output.produce(buffer);
									return this;
								}
								@Override
								public HttpAsyncOutput ok() {
									output.ok();
									return this;
								}
								@Override
								public HttpAsyncOutput notFound() {
									output.notFound();
									return this;
								}
								@Override
								public HttpAsyncOutput internalServerError() {
									output.internalServerError();
									return this;
								}
								@Override
								public HttpAsyncOutput badRequest() {
									output.badRequest();
									return this;
								}
								@Override
								public HttpAsyncOutput header(String key, String value) {
									output.header(key, value);
									return this;
								}
								@Override
								public HttpAsyncOutput contentType(String contentType) {
									output.contentType(contentType);
									return this;
								}
								@Override
								public HttpAsyncOutput contentLength(long contentLength) {
									output.contentLength(jsonpHeaderAsBytes.length + contentLength + jsonpFooterAsBytes.length);
									return this;
								}
							});
						}
					});
				} else { // if (http.contentType().contains(HttpContentType.json().asString())) {
					http.contentType("application/javascript");
					http.contentLength(jsonpHeaderAsBytes.length + http.contentLength() + jsonpFooterAsBytes.length);

					final InputStream stream = http.stream();
					if (stream != null) {
						http.stream(new SequenceInputStream(new ByteArrayInputStream(jsonpHeaderAsBytes), new SequenceInputStream(stream, new ByteArrayInputStream(jsonpFooterAsBytes))));
					} else {
						http.content(jsonp + "(" + http.content() + ");");
					}
				}
			}
		});
	}

	@Route(method = HttpMethod.GET)
	public Http addJsonpToGet(@QueryParameter("jsonp") String jsonp) {
		return wrap(jsonp);
	}
	@Route(method = HttpMethod.PUT)
	public Http addJsonpToPut(@QueryParameter("jsonp") String jsonp) {
		return wrap(jsonp);
	}
	@Route(method = HttpMethod.POST)
	public Http addJsonpToPost(@QueryParameter("jsonp") String jsonp) {
		return wrap(jsonp);
	}
	@Route(method = HttpMethod.DELETE)
	public Http addJsonpToDelete(@QueryParameter("jsonp") String jsonp) {
		return wrap(jsonp);
	}
	@Route(method = HttpMethod.HEAD)
	public Http addJsonpToHead(@QueryParameter("jsonp") String jsonp) {
		return wrap(jsonp);
	}
}
