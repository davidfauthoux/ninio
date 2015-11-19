package com.davfx.ninio.http.util.controllers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import com.davfx.ninio.http.HttpHeaderValue;
import com.davfx.ninio.http.HttpMethod;
import com.davfx.ninio.http.util.HttpController;
import com.davfx.ninio.http.util.annotations.QueryParameter;
import com.davfx.ninio.http.util.annotations.Route;
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
								public void close() {
									output.produce(ByteBuffer.wrap(jsonpFooterAsBytes));
									output.close();
								}
								@Override
								public void failed(IOException e) {
									output.failed(e);
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
								public HttpAsyncOutput header(String key, HttpHeaderValue value) {
									output.header(key, value);
									return this;
								}
								@Override
								public HttpAsyncOutput contentType(HttpHeaderValue contentType) {
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
					http.contentType(HttpHeaderValue.simple("application/javascript"));
					http.contentLength(jsonpHeaderAsBytes.length + http.contentLength() + jsonpFooterAsBytes.length);

					final HttpStream stream = http.stream();
					if (stream != null) {
						http.stream(new HttpStream() {
							@Override
							public void produce(OutputStream output) throws Exception {
								output.write(jsonpHeaderAsBytes);
								stream.produce(output);
								output.write(jsonpFooterAsBytes);
							}
						});
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
