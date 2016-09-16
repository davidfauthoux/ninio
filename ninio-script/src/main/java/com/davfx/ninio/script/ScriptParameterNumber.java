package com.davfx.ninio.script;

public final class ScriptParameterNumber implements ScriptParameter {
	private final double value;
	public ScriptParameterNumber(double value) {
		this.value = value;
	}
	
	@Override
	public ScriptElement build(ScriptElementBuilder builder) {
		return builder.number(value);
	}
}
