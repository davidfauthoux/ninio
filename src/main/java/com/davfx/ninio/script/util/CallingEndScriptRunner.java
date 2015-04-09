package com.davfx.ninio.script.util;

import com.davfx.ninio.common.Failable;
import com.davfx.ninio.script.ExecutorScriptRunner;
import com.davfx.ninio.script.SyncScriptFunction;
import com.davfx.util.AppendIterable;
import com.davfx.util.ConfigUtils;
import com.davfx.util.PrependIterable;
import com.google.gson.JsonElement;


public final class CallingEndScriptRunner {
	private static final String END_FUNCTION_NAME = ConfigUtils.load(CallingEndScriptRunner.class).getString("script.functions.end");

	private final RegisteredFunctionsScriptRunner wrappee;

	public CallingEndScriptRunner(RegisteredFunctionsScriptRunner wrappee) {
		this.wrappee = wrappee;
		wrappee.register(CallingEndScriptRunner.END_FUNCTION_NAME);
	}

	public void link(final Runnable onEnd) {
		wrappee.link(CallingEndScriptRunner.END_FUNCTION_NAME, new SyncScriptFunction<JsonElement>() {
			@Override
			public JsonElement call(JsonElement request) {
				onEnd.run();
				return null;
			}
		});
	}
	
	public void prepare(Iterable<String> script, Failable fail) {
		String underlyingFunctionVar = '\'' + CallingEndScriptRunner.class.getCanonicalName() + '\'';
		String underlyingCountVar = '\'' + CallingEndScriptRunner.class.getCanonicalName() + "_count'";
		
		script = new PrependIterable<String>("this[" + underlyingFunctionVar + "] = " + ExecutorScriptRunner.CALL_FUNCTION_NAME + ";"
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
		
		wrappee.prepare(script, fail);
	}
	
	public void eval(Iterable<String> script, Failable fail) {
		String underlyingCountVar = '\'' + CallingEndScriptRunner.class.getCanonicalName() + "_count'";

		script = new PrependIterable<String>("this[" + underlyingCountVar + "] = 0;", script);
		script = new AppendIterable<String>(script, "if (this[" + underlyingCountVar + "] == 0) {"
														+ END_FUNCTION_NAME + "();"
													+ "}");
		
		wrappee.eval(script, fail);
	}
}
