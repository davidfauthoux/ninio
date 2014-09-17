package com.davfx.ninio.script;

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;

public final class BasicScript implements Script {
	private final Deque<String> list = new LinkedList<String>();
	public BasicScript() {
	}
	@Override
	public Script append(String script) {
		list.addLast(script);
		return this;
	}
	@Override
	public Script prepend(String script) {
		list.addFirst(script);
		return this;
	}
	
	@Override
	public Iterator<String> iterator() {
		return list.iterator();
	}
}
