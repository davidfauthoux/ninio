package com.davfx.ninio.http;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMultimap;

public final class HttpPath {
	public static void main(String[] args) {
		System.out.println(Splitter.on('/').splitToList(""));
	}
	public final String line;
	public final String path;
	public final ImmutableMultimap<String, String> parameters;
	
	private static String path(String p, int end) {
		if (p.isEmpty()) {
			throw new IllegalArgumentException("Invalid empty path");
		}
		if (p.charAt(0) != HttpSpecification.PATH_SEPARATOR) {
			throw new IllegalArgumentException("Path must start with '" + HttpSpecification.PATH_SEPARATOR + "': " + p);
		}
		String s;
		if (end < 0) {
			s = p.substring(1);
		} else {
			s = p.substring(1, end);
		}
		Deque<String> l = new LinkedList<>();
		for (String k : Splitter.on(HttpSpecification.PATH_SEPARATOR).splitToList(s)) {
			if (k.equals(".")) {
				continue;
			}
			if (k.equals("..")) {
				l.removeLast();
				continue;
			}
			l.add(k);
		}
		return Joiner.on(HttpSpecification.PATH_SEPARATOR).join(l);
	}
	
	public HttpPath(String line) {
		this.line = line;
		
		int i = line.indexOf(HttpSpecification.PARAMETERS_START);
		if (i < 0) {
			parameters = ImmutableMultimap.<String, String>of();
			int j = line.indexOf(HttpSpecification.HASH_SEPARATOR);
			if (j < 0) {
				path = path(line, -1);
			} else {
				path = path(line, j);
			}
		} else {
			path = path(line, i);
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
