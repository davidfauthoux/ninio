package com.davfx.ninio.http;

import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;

public final class HttpHeaderValue {
	
	public static final HttpHeaderValue GZIP = HttpHeaderValue.simple("gzip");
	public static final HttpHeaderValue CLOSE = HttpHeaderValue.simple("close");
	public static final HttpHeaderValue KEEP_ALIVE = HttpHeaderValue.simple("keep-alive");
	public static final HttpHeaderValue CHUNKED = HttpHeaderValue.simple("chunked");

	public final ImmutableList<String> values;
	public final ImmutableMultimap<String, Optional<String>> extensions;

	public HttpHeaderValue(ImmutableList<String> values, ImmutableMultimap<String, Optional<String>> extensions) {
		this.values = values;
		this.extensions = extensions;
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append(Joiner.on(HttpSpecification.MULTIPLE_SEPARATOR).join(values));
		for (Map.Entry<String, Optional<String>> e : extensions.entries()) {
			b.append(HttpSpecification.EXTENSION_SEPARATOR).append(UrlUtils.encode(e.getKey()));
			Optional<String> o = e.getValue();
			if (o.isPresent()) {
				b.append(HttpSpecification.PARAMETER_KEY_VALUE_SEPARATOR).append(UrlUtils.encode(o.get()));
			}
		}
		return b.toString();
	}
	
	public boolean contains(String val) {
		for (String s : values) {
			if (s.equalsIgnoreCase(val)) {
				return true;
			}
		}
		return false;
	}
	
	public String asString() {
		if (values.isEmpty()) {
			return "";
		}
		return values.get(0);
	}
	public int asInt() {
		if (values.isEmpty()) {
			return 0;
		}
		try {
			return Integer.parseInt(values.get(0));
		} catch (NumberFormatException nfe) {
			return 0;
		}
	}
	public long asLong() {
		if (values.isEmpty()) {
			return 0L;
		}
		try {
			return Long.parseLong(values.get(0));
		} catch (NumberFormatException nfe) {
			return 0L;
		}
	}

	public static HttpHeaderValue simple(String value) {
		return new HttpHeaderValue(ImmutableList.of(value), ImmutableMultimap.<String, Optional<String>>of());
	}
	public static HttpHeaderValue of(String value, String extensionKey, String extensionValue) {
		return new HttpHeaderValue(ImmutableList.of(value), ImmutableMultimap.<String, Optional<String>>of(extensionKey, Optional.of(extensionValue)));
	}
	public static HttpHeaderValue of(String line) {
		int i = line.indexOf(HttpSpecification.EXTENSION_SEPARATOR);
		if (i < 0) {
			return new HttpHeaderValue(ImmutableList.copyOf(Splitter.on(HttpSpecification.MULTIPLE_SEPARATOR).splitToList(line.trim())), ImmutableMultimap.<String, Optional<String>>of());
		}
		
		String value = line.substring(0, i);
		ImmutableMultimap.Builder<String, Optional<String>> m = ImmutableMultimap.builder();
		for (String kv : Splitter.on(HttpSpecification.EXTENSION_SEPARATOR).splitToList(line.substring(i + 1))) {
			kv = kv.trim();
			if (kv.isEmpty()) {
				continue;
			}
			List<String> l = Splitter.on(HttpSpecification.EXTENSION_SEPARATOR).splitToList(kv);
			if (!l.isEmpty()) {
				if (l.size() == 1) {
					m.put(UrlUtils.decode(l.get(0).trim()), Optional.<String>absent());
				} else {
					m.put(UrlUtils.decode(l.get(0).trim()), Optional.of(UrlUtils.decode(l.get(1).trim())));
				}
			}
		}
		
		return new HttpHeaderValue(ImmutableList.copyOf(Splitter.on(HttpSpecification.MULTIPLE_SEPARATOR).splitToList(value.trim())), m.build());
	}
}
