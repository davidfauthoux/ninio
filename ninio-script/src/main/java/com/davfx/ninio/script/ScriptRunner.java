package com.davfx.ninio.script;

import java.util.Map;

public interface ScriptRunner extends ScriptElementBuilder {
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

		void eval(String script, Map<String, ?> parameters, End end);

		Engine sub();
	}
	Engine engine();
}
