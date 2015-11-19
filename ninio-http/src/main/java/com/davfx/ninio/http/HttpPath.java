package com.davfx.ninio.http;

import java.util.List;
import java.util.Map;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMultimap;

public final class HttpPath {
	public static final HttpPath ROOT = new HttpPath(HttpQueryPath.of(), ImmutableMultimap.<String, Optional<String>>of(), null);

	public final HttpQueryPath path;
	public final String hash;
	public final ImmutableMultimap<String, Optional<String>> parameters;
	
	public HttpPath(HttpQueryPath path, ImmutableMultimap<String, Optional<String>> parameters, String hash) {
		this.path = path;
		this.parameters = parameters;
		this.hash = hash;
	}

	public static HttpPath of(String line) {
		ImmutableMultimap<String, Optional<String>> parameters;
		HttpQueryPath path;
		String hash;
		
		int i = line.indexOf(HttpSpecification.PARAMETERS_START);
		if (i < 0) {
			parameters = ImmutableMultimap.of();
			int j = line.indexOf(HttpSpecification.HASH_SEPARATOR);
			if (j < 0) {
				hash = null;
				path = HttpQueryPath.of(line);
			} else {
				hash = line.substring(j + 1);
				path = HttpQueryPath.of(line.substring(0, j));
			}
		} else {
			path = HttpQueryPath.of(line.substring(0, i));
			int j = line.indexOf(HttpSpecification.HASH_SEPARATOR);
			String s;
			if (j < 0) {
				hash = null;
				s = line.substring(i + 1);
			} else {
				hash = line.substring(j + 1);
				s = line.substring(i + 1, j);
			}
			ImmutableMultimap.Builder<String, Optional<String>> m = ImmutableMultimap.builder();
			for (String kv : Splitter.on(HttpSpecification.PARAMETERS_SEPARATOR).splitToList(s)) {
				List<String> l = Splitter.on(HttpSpecification.PARAMETER_KEY_VALUE_SEPARATOR).splitToList(kv);
				if (!l.isEmpty()) {
					if (l.size() == 1) {
						m.put(UrlUtils.decode(l.get(0)), Optional.<String>absent());
					} else {
						m.put(UrlUtils.decode(l.get(0)), Optional.of(UrlUtils.decode(l.get(1))));
					}
				}
			}
			parameters = m.build();
		}
		
		return new HttpPath(path, parameters, hash);
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append(path.toString());
		if (!parameters.isEmpty()) {
			boolean first = true;
			for (Map.Entry<String, Optional<String>> e : parameters.entries()) {
				if (first) {
					b.append(HttpSpecification.PARAMETERS_START);
					first = false;
				} else {
					b.append(HttpSpecification.PARAMETERS_SEPARATOR);
				}
				b.append(UrlUtils.encode(e.getKey()));
				Optional<String> o = e.getValue();
				if (o.isPresent()) {
					b.append(HttpSpecification.PARAMETER_KEY_VALUE_SEPARATOR);
					b.append(UrlUtils.encode(o.get()));
				}
			}
		}
		if (hash != null) {
			b.append(HttpSpecification.HASH_SEPARATOR).append(hash);
		}
		return b.toString();
	}
}
