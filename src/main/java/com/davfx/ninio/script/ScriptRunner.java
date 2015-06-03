package com.davfx.ninio.script;

import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Failable;

public interface ScriptRunner extends AutoCloseable, Closeable {
	void register(String function, SyncScriptFunction syncFunction);
	void register(String function, AsyncScriptFunction asyncFunction);

	void prepare(String script, Failable fail, Runnable end);
	
	interface Engine {
		void register(String function, SyncScriptFunction syncFunction);
		void register(String function, AsyncScriptFunction asyncFunction);

		void eval(String script, Failable fail, Runnable end);
	}
	Engine engine();
}
