package com.davfx.ninio.script.util;

import java.util.Deque;
import java.util.LinkedList;

import com.davfx.ninio.common.Failable;
import com.davfx.ninio.script.SimpleScriptRunner;
import com.davfx.util.PrependIterable;

public final class PrependerSimpleScriptRunner implements SimpleScriptRunner {
	private final SimpleScriptRunner wrappee;
	private final Deque<String> toPrepend = new LinkedList<>();
	public PrependerSimpleScriptRunner(SimpleScriptRunner wrappee) {
		this.wrappee = wrappee;
	}
	
	public PrependerSimpleScriptRunner add(String s) {
		toPrepend.addFirst(s);
		return this;
	}
	
	@Override
	public void eval(Iterable<String> script, Failable fail) {
		for (String s : toPrepend) {
			script = new PrependIterable<String>(s, script);
		}
		wrappee.eval(script, fail);
	}
}
