package com.davfx.ninio.http;

import java.util.List;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMultimap;

public final class HttpPath {
	public static void main(String[] args) {
		System.out.println(Splitter.on('/').splitToList(""));
	}
	public final String line;
	public final HttpQueryPath path;
	public final ImmutableMultimap<String, String> parameters;
	
	public HttpPath(String line) {
		this.line = line;
		
		int i = line.indexOf(HttpSpecification.PARAMETERS_START);
		if (i < 0) {
			parameters = ImmutableMultimap.<String, String>of();
			int j = line.indexOf(HttpSpecification.HASH_SEPARATOR);
			if (j < 0) {
				path = HttpQueryPath.of(line);
			} else {
				path = HttpQueryPath.of(line.substring(0, j));
			}
		} else {
			path = HttpQueryPath.of(line.substring(0, i));
			int j = line.indexOf(HttpSpecification.HASH_SEPARATOR);
			String s;
			if (j < 0) {
				s = line.substring(i + 1);
			} else {
				s = line.substring(i + 1, j);
			}
			ImmutableMultimap.Builder<String, String> m = ImmutableMultimap.<String, String>builder();
			for (String kv : Splitter.on(HttpSpecification.PARAMETERS_SEPARATOR).splitToList(s)) {
				List<String> l = Splitter.on(HttpSpecification.PARAMETER_KEY_VALUE_SEPARATOR).splitToList(kv);
				if (!l.isEmpty()) {
					if (l.size() == 1) {
						m.put(UrlUtils.decode(l.get(0)), "");
					} else {
						m.put(UrlUtils.decode(l.get(0)), UrlUtils.decode(l.get(1)));
					}
				}
			}
			parameters = m.build();
		}
	}
	
	@Override
	public String toString() {
		return line;
	}
}
