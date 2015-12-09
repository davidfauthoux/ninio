package com.davfx.ninio.http;

import java.util.Deque;
import java.util.LinkedList;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

public final class HttpQueryPath {
	public final ImmutableList<String> path;
	public HttpQueryPath(ImmutableList<String> path) {
		this.path = path;
	}
	
	@Override
	public String toString() {
		return HttpSpecification.PATH_SEPARATOR + Joiner.on(HttpSpecification.PATH_SEPARATOR).join(path);
	}

	@Override
	public int hashCode() {
		return path.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof HttpQueryPath)) {
			return false;
		}
		HttpQueryPath other = (HttpQueryPath) obj;
		return path.equals(other.path);
	}

	public static HttpQueryPath of() {
		return new HttpQueryPath(ImmutableList.<String>of());
	}
	public static HttpQueryPath of(String p) {
		return of(HttpQueryPath.of(), p);
	}
	public static HttpQueryPath of(HttpQueryPath root, String p) {
		if (p.isEmpty()) {
			throw new IllegalArgumentException("Invalid empty path");
		}
		if (p.charAt(0) != HttpSpecification.PATH_SEPARATOR) {
			throw new IllegalArgumentException("Path must start with '" + HttpSpecification.PATH_SEPARATOR + "': " + p);
		}
		String s = p.substring(1);
		Deque<String> l = new LinkedList<>(root.path);
		for (String k : Splitter.on(HttpSpecification.PATH_SEPARATOR).splitToList(s)) {
			if (k.isEmpty()) {
				continue;
			}
			if (k.equals(".")) {
				continue;
			}
			if (k.equals("..")) {
				l.removeLast();
				continue;
			}
			l.add(k);
		}
		return new HttpQueryPath(ImmutableList.copyOf(l));
	}
	
}
