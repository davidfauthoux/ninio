package com.davfx.ninio.http;

import java.util.Iterator;

public final class SubPathHttpRequestFilter implements HttpRequestFilter {

	private final HttpQueryPath parent;
	
	public SubPathHttpRequestFilter(HttpQueryPath parent) {
		this.parent = parent;
	}
	
	@Override
	public boolean accept(HttpRequest request) {
		Iterator<String> i = request.path.path.path.iterator();
		Iterator<String> j = parent.path.iterator();
		while (i.hasNext()) {
			if (!j.hasNext()) {
				return true;
			}
			String s = i.next();
			String t = j.next();
			if (!s.equals(t)) {
				return false;
			}
		}
		return !j.hasNext();
	}
}
