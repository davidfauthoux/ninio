package com.davfx.ninio.script;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.davfx.ninio.common.Failable;
import com.davfx.util.PrependIterable;
import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public final class RegisteredFunctionsScriptRunner implements SimpleScriptRunnerScriptRegister {
	private final ScriptRunner<JsonElement> wrappee;
	private final Map<String, AsyncScriptFunction<JsonElement>> asyncFunctions = new HashMap<>();
	private final Map<String, SyncScriptFunction<JsonElement>> syncFunctions = new HashMap<>();
	private final List<String> functionNames = new LinkedList<>();

	public RegisteredFunctionsScriptRunner(ScriptRunner<JsonElement> wrappee) {
		this.wrappee = wrappee;
	}
	
	@Override
	public RegisteredFunctionsScriptRunner register(String functionId, AsyncScriptFunction<JsonElement> function) {
		asyncFunctions.put(functionId, function);
		functionNames.add(functionId);
		return this;
	}
	@Override
	public RegisteredFunctionsScriptRunner register(String functionId, SyncScriptFunction<JsonElement> function) {
		syncFunctions.put(functionId, function);
		functionNames.add(functionId);
		return this;
	}
	
	@Override
	public void eval(Iterable<String> script, Failable fail) {
		for (String f : Lists.reverse(functionNames)) {
			script = new PrependIterable<String>("var " + f + " = function(parameter, callback) {"
												+ "return " + ExecutorScriptRunner.CALL_FUNCTION_NAME + "({'f': '" + f + "', 'p': parameter}, callback);"
											+ "};", script);
		}
		
		wrappee.eval(script, fail, new AsyncScriptFunction<JsonElement>() {
			@Override
			public void call(JsonElement request, AsyncScriptFunction.Callback<JsonElement> callback) {
				JsonObject o = request.getAsJsonObject();
				String functionId = o.get("f").getAsString();
				JsonElement parameter = o.get("p");
				if ((parameter != null) && parameter.equals(JsonNull.INSTANCE)) {
					parameter = null;
				}
				AsyncScriptFunction<JsonElement> function = asyncFunctions.get(functionId);
				if (function == null) {
					throw new RuntimeException("Undefined function: " + functionId);
				}
				function.call(parameter, callback);
			}
		}, new SyncScriptFunction<JsonElement>() {
			@Override
			public JsonElement call(JsonElement request) {
				JsonObject o = request.getAsJsonObject();
				String functionId = o.get("f").getAsString();
				JsonElement parameter = o.get("p");
				if ((parameter != null) && parameter.equals(JsonNull.INSTANCE)) {
					parameter = null;
				}
				SyncScriptFunction<JsonElement> function = syncFunctions.get(functionId);
				if (function == null) {
					throw new RuntimeException("Undefined function: " + functionId);
				}
				return function.call(parameter);
			}
		});
	}
}
