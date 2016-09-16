package com.davfx.ninio.script;

import com.google.common.collect.ImmutableMap;

public interface ScriptRunner {
	interface End {
		void failed(Exception e);
		void ended();
	}
	
	/*%%
	<T, U> void register(String function, SyncScriptFunction<T, U> syncFunction);
	<T, U> void register(String function, AsyncScriptFunction<T, U> asyncFunction);

	void prepare(String script, End end);
	*/
	
	interface Engine {
		void register(String function, SyncScriptFunction syncFunction);
		void register(String function, AsyncScriptFunction asyncFunction);

		void eval(String script, ImmutableMap<String, ScriptParameter> parameters, End end);

		Engine sub();
	}
	Engine engine();
}
