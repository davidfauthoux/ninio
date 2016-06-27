package com.davfx.ninio.script;

public interface ScriptElementBuilder {
	ScriptElement string(String value);
	ScriptElement number(double value);
	ScriptObjectBuilder object();
	ScriptArrayBuilder array();
}
