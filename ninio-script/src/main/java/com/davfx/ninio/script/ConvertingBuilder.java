package com.davfx.ninio.script;

interface ConvertingBuilder extends ScriptElementBuilder {
	String toJsScript(String parameterName);
	String fromJsScript(String parameterName);
	
	ScriptElement toJava(Object o);
	Object fromJava(ScriptElement e);
}