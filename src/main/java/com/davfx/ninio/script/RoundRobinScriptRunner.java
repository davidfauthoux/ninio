package com.davfx.ninio.script;

import java.util.ArrayList;
import java.util.List;

import com.davfx.ninio.common.Failable;

public final class RoundRobinScriptRunner<T> implements ScriptRunner<T> {
	public final List<ScriptRunner<T>> runners = new ArrayList<>();
	private int index = 0;

	public RoundRobinScriptRunner() {
	}
	
	public RoundRobinScriptRunner<T> add(ScriptRunner<T> runner) {
		runners.add(runner);
		return this;
	}

	@Override
	public void prepare(Iterable<String> script, Failable fail) {
		for (ScriptRunner<T> r : runners) {
			r.prepare(script, fail);
		}
	}
	
	@Override
	public void eval(Iterable<String> script, Failable fail, Runnable end, AsyncScriptFunction<T> asyncFunction, SyncScriptFunction<T> syncFunction) {
		int i = index;
		index = (index + 1) % runners.size();
		ScriptRunner<T> r = runners.get(i);
		r.eval(script, fail, end, asyncFunction, syncFunction);
	}

	public void close() {
		for (ScriptRunner<T> r : runners) {
			r.close();
		}
	}
}
