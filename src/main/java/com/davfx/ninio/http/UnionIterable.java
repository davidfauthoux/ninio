package com.davfx.ninio.http;

import java.util.Iterator;

public final class UnionIterable<T> implements Iterable<T> {
	private final Iterable<T> i;
	private final Iterable<T> j;
	
	public UnionIterable(Iterable<T> i, Iterable<T> j) {
		this.i = i;
		this.j = j;
	}

	@Override
	public Iterator<T> iterator() {
		final Iterator<T> ii = i.iterator();
		final Iterator<T> ji = j.iterator();
		return new Iterator<T>() {
			@Override
			public boolean hasNext() {
				return ii.hasNext() || ji.hasNext();
			}
			@Override
			public T next() {
				if (ii.hasNext()) {
					return ii.next();
				}
				return ji.next();
			}
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
}
