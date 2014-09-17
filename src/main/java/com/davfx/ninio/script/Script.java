package com.davfx.ninio.script;

public interface Script extends Iterable<String> {
	Script prepend(String script);
	Script append(String script);
}
