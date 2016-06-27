package com.davfx.ninio.script;

public interface ScriptElement {
	boolean isUndefined();
	ScriptObject asObject();
	ScriptArray asArray();
	ScriptNumber asNumber();
	ScriptString asString();
}
