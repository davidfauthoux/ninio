package com.davfx.ninio.script;

public interface ScriptObject extends ScriptElement {
	ScriptElement get(String key);
	
	interface Entry {
		String key();
		ScriptElement value();
	}
	Iterable<Entry> entries();
}
