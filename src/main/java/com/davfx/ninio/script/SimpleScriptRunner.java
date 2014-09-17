package com.davfx.ninio.script;

import com.davfx.ninio.common.Failable;

public interface SimpleScriptRunner {
	void eval(Iterable<String> script, Failable fail);
}
