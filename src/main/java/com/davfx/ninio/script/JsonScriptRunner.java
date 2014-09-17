package com.davfx.ninio.script;

import com.davfx.ninio.common.Failable;
import com.davfx.util.PrependIterable;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;

public final class JsonScriptRunner implements ScriptRunner<JsonElement> {
	private final ScriptRunner<String> wrappee;

	public JsonScriptRunner(ScriptRunner<String> wrappee) {
		this.wrappee = wrappee;
	}

	@Override
	public void eval(Iterable<String> script, Failable fail, AsyncScriptFunction<JsonElement> asyncFunction, SyncScriptFunction<JsonElement> syncFunction) {
		String underlyingFunctionVar = '\'' + JsonScriptRunner.class.getCanonicalName() + '\'';
		script = new PrependIterable<String>("this[" + underlyingFunctionVar + "] = " + ExecutorScriptRunner.CALL_FUNCTION_NAME + ";"
				+ ExecutorScriptRunner.CALL_FUNCTION_NAME + " = function(parameter, callback) {"
					+ "return JSON.parse(this[" + underlyingFunctionVar + "](JSON.stringify(parameter), (callback == undefined) ? undefined : function(p) { callback(JSON.parse(p)); }));"
				+ "};", script);
		wrappee.eval(script, fail, new AsyncScriptFunction<String>() {
			@Override
			public void call(String request, AsyncScriptFunction.Callback<String> callback) {
				asyncFunction.call((request == null) ? null : new JsonParser().parse(request), new AsyncScriptFunction.Callback<JsonElement>() {
					@Override
					public void handle(JsonElement response) {
						if (response == null) {
							callback.handle(null);
							return;
						}
						if (response.equals(JsonNull.INSTANCE)) {
							callback.handle(null);
							return;
						}
						callback.handle(response.toString());
					}
				});
			}
		}, new SyncScriptFunction<String>() {
			@Override
			public String call(String request) {
				JsonElement r = syncFunction.call((request == null) ? null : new JsonParser().parse(request));
				if (r == null) {
					return null;
				}
				if (r.equals(JsonNull.INSTANCE)) {
					return null;
				}
				return r.toString();
			}
		});
	}
}
