package com.davfx.ninio.script.util;

import com.davfx.ninio.common.Failable;
import com.davfx.ninio.script.ExecutorScriptRunner;
import com.davfx.ninio.script.SimpleScriptRunner;
import com.davfx.util.AppendIterable;
import com.davfx.util.ConfigUtils;
import com.davfx.util.PrependIterable;


public final class CallingEndScriptRunner implements SimpleScriptRunner {
	public static final String END_FUNCTION_NAME = ConfigUtils.load(CallingEndScriptRunner.class).getString("script.functions.end");

	private final SimpleScriptRunner wrappee;

	public CallingEndScriptRunner(SimpleScriptRunner wrappee) {
		this.wrappee = wrappee;
	}

	@Override
	public void eval(Iterable<String> script, Failable fail) {
		String underlyingFunctionVar = '\'' + CallingEndScriptRunner.class.getCanonicalName() + '\'';
		String underlyingCountVar = '\'' + CallingEndScriptRunner.class.getCanonicalName() + "_count'";
		script = new PrependIterable<String>("this[" + underlyingFunctionVar + "] = " + ExecutorScriptRunner.CALL_FUNCTION_NAME + ";"
				+ "this[" + underlyingCountVar + "] = 0;"
				+ ExecutorScriptRunner.CALL_FUNCTION_NAME + " = function(parameter, callback) { "
						+ "if (callback == undefined) {"
							+ "return this[" + underlyingFunctionVar + "](parameter);"
						+ "} else {"
							+ "this[" + underlyingCountVar + "]++;"
							+ "this[" + underlyingFunctionVar + "](parameter, function(p) {"
								+ "this[" + underlyingCountVar + "]--;"
								+ "callback(p);"
								+ "if (this[" + underlyingCountVar + "] == 0) {"
										+ END_FUNCTION_NAME + "();"
								+ "};"
								/*
								+ "if (p == undefined) {" // This algorithm does not check that callback() is called multiple times erroneously
									+ "this[" + underlyingCountVar + "]--;"
									+ "if (this[" + underlyingCountVar + "] == 0) {"
											+ END_FUNCTION_NAME + "();"
									+ "}"
								+ "} else {"
									+ "callback(p);"
								+ "};"
								*/
							+ "});"
						+ "}"
					+ "};", script);
		script = new AppendIterable<String>(script, "if (this[" + underlyingCountVar + "] == 0) {"
							+ END_FUNCTION_NAME + "();"
					+ "}");
		
		wrappee.eval(script, fail);
	}
}
