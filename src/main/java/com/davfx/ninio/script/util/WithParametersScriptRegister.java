package com.davfx.ninio.script.util;

import java.util.HashMap;
import java.util.Map;

import com.davfx.ninio.script.AsyncScriptFunction;
import com.davfx.ninio.script.ScriptRegister;
import com.davfx.ninio.script.SyncScriptFunction;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class WithParametersScriptRegister {
	private final ScriptRegister scriptRegister;
	private final PrependerSimpleScriptRunner prependerSimpleScriptRunner;
	public WithParametersScriptRegister(ScriptRegister scriptRegister, PrependerSimpleScriptRunner prependerSimpleScriptRunner) {
		this.scriptRegister = scriptRegister;
		this.prependerSimpleScriptRunner = prependerSimpleScriptRunner;
	}
	
	public static interface WithParametersAsyncFunction {
		void call(Map<String, JsonElement> request, AsyncScriptFunction.Callback<JsonElement> callback);
	}
	public static interface WithParametersSyncFunction {
		JsonElement call(Map<String, JsonElement> request);
	}

	public WithParametersScriptRegister register(String functionId, final String[] parameters, final WithParametersAsyncFunction function) {
		StringBuilder b = new StringBuilder();
		b.append("var _").append(functionId).append(" = ").append(functionId).append(";");
		b.append("var ").append(functionId).append(" = function(");
		for (String p : parameters) {
			b.append(p).append(',');
		}
		b.append("callback) { _").append(functionId).append("({");
		boolean first = true;
		for (String p : parameters) {
			if (first) {
				first = false;
			} else {
				b.append(',');
			}
			b.append(p).append(':').append(p);
		}
		b.append("}, callback); }");
		prependerSimpleScriptRunner.add(b.toString());
		
		scriptRegister.register("_" + functionId, new AsyncScriptFunction<JsonElement>() {
			@Override
			public void call(JsonElement request, Callback<JsonElement> callback) {
				JsonObject o = request.getAsJsonObject();
				Map<String, JsonElement> m = new HashMap<>();
				for (String p : parameters) {
					m.put(p, o.get(p));
				}
				function.call(m, callback);
			}
		});
		return this;
	}
	public WithParametersScriptRegister register(String functionId, final String[] parameters, final WithParametersSyncFunction function) {
		StringBuilder b = new StringBuilder();
		b.append("var _").append(functionId).append(" = ").append(functionId).append(";");
		b.append("var ").append(functionId).append(" = function(");
		boolean first = true;
		for (String p : parameters) {
			if (first) {
				first = false;
			} else {
				b.append(',');
			}
			b.append(p);
		}
		b.append(") { return _").append(functionId).append("({");
		first = true;
		for (String p : parameters) {
			if (first) {
				first = false;
			} else {
				b.append(',');
			}
			b.append(p).append(':').append(p);
		}
		b.append("}); }");
		prependerSimpleScriptRunner.add(b.toString());
		
		scriptRegister.register(functionId, new SyncScriptFunction<JsonElement>() {
			@Override
			public JsonElement call(JsonElement request) {
				JsonObject o = request.getAsJsonObject();
				Map<String, JsonElement> m = new HashMap<>();
				for (String p : parameters) {
					m.put(p, o.get(p));
				}
				return function.call(m);
			}
		});
		return this;
	}

}
