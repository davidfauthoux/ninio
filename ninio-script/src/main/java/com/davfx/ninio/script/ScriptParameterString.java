package com.davfx.ninio.script;

public final class ScriptParameterString implements ScriptParameter {
	private final String value;
	public ScriptParameterString(String value) {
		this.value = value;
	}
	
	@Override
	public ScriptElement build(ScriptElementBuilder builder) {
		return builder.string(value);
	}
}
