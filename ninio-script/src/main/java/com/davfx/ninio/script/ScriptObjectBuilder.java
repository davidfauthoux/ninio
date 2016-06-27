package com.davfx.ninio.script;

public interface ScriptObjectBuilder {
	ScriptObjectBuilder put(String key, ScriptElement value);
	
	ScriptObject build();
}
