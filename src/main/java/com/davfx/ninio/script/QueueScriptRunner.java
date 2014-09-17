package com.davfx.ninio.script;

import com.davfx.ninio.common.Failable;
import com.davfx.ninio.common.Queue;

public final class QueueScriptRunner<T> implements ScriptRunner<T> {
	private final Queue queue;
	private final ScriptRunner<T> wrappee;

	public QueueScriptRunner(Queue queue, ScriptRunner<T> wrappee) {
		this.queue = queue;
		this.wrappee = wrappee;
	}
	
	
	@Override
	public void eval(Iterable<String> script, Failable fail, AsyncScriptFunction<T> asyncFunction, SyncScriptFunction<T> syncFunction) {
		wrappee.eval(script, fail, new AsyncScriptFunction<T>() {
			@Override
			public void call(T request, AsyncScriptFunction.Callback<T> callback) {
				queue.post(new Runnable() {
					@Override
					public void run() {
						asyncFunction.call(request, callback);
					}
				});
			}
		}, syncFunction);
	}
}
