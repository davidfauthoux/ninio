package com.davfx.ninio.script;

public interface ScriptElement {
	ScriptObject asObject();
	ScriptArray asArray();
	ScriptNumber asNumber();
	ScriptString asString();
}
