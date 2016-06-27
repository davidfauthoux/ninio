package com.davfx.ninio.script;

public interface ScriptArrayBuilder {
	ScriptArrayBuilder add(ScriptElement value);
	
	ScriptArray build();
}
