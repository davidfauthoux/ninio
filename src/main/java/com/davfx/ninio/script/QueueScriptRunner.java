package com.davfx.ninio.script;

import com.davfx.ninio.common.Failable;
import com.davfx.ninio.common.Queue;
import com.google.gson.JsonElement;

public final class QueueScriptRunner implements ScriptRunner {
	private final Queue queue;
	private final ScriptRunner wrappee;

	public QueueScriptRunner(Queue queue, ScriptRunner wrappee) {
		this.queue = queue;
		this.wrappee = wrappee;
	}
	
	@Override
	public void prepare(String script, Failable fail, Runnable end) {
		wrappee.prepare(script, fail, end);
	}

	@Override
	public void register(String function, SyncScriptFunction syncFunction) {
		wrappee.register(function, syncFunction);
	}
	
	@Override
	public void register(String function, final AsyncScriptFunction asyncFunction) {
		wrappee.register(function, new AsyncScriptFunction() {
			@Override
			public void call(final JsonElement request, final AsyncScriptFunction.Callback callback) {
				queue.post(new Runnable() {
					@Override
					public void run() {
						asyncFunction.call(request, callback);
					}
				});
			}
		});
	}
	
	@Override
	public Engine engine() {
		final Engine e = wrappee.engine();
		return new Engine() {
			@Override
			public void eval(String script, Failable fail, Runnable end) {
				e.eval(script, fail, end);
			}
			
			@Override
			public void register(String function, SyncScriptFunction syncFunction) {
				e.register(function, syncFunction);
			}
			
			@Override
			public void register(String function, final AsyncScriptFunction asyncFunction) {
				e.register(function, new AsyncScriptFunction() {
					@Override
					public void call(final JsonElement request, final AsyncScriptFunction.Callback callback) {
						queue.post(new Runnable() {
							@Override
							public void run() {
								asyncFunction.call(request, callback);
							}
						});
					}
				});
			}
		};
	}
	
	@Override
	public void close() {
		wrappee.close();
	}
}
