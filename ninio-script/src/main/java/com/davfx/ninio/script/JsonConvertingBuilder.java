package com.davfx.ninio.script;

import java.util.Iterator;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

final class JsonConvertingBuilder implements ConvertingBuilder {
	
	public JsonConvertingBuilder() {
	}
	
	public String fromJsScript(String parameterName) {
		return ""
				+ "if (" + parameterName + " == undefined) return null;"
				+ "return JSON.stringify(" + parameterName + ");";
	}
	@Override
	public String toJsScript(String parameterName) {
		return ""
				+ "if (" + parameterName + " == null) return null;"
				+ "return JSON.parse(" + parameterName + ");";
	}
	
	private static interface Internal {
		JsonElement toJs();
	}
	
	private static final class InternalScriptString implements ScriptString, JsonConvertingBuilder.Internal {
		private final JsonPrimitive value;
		public InternalScriptString(JsonPrimitive value) {
			if (!value.isJsonPrimitive() || value.isJsonNull()) {
				throw new IllegalArgumentException();
			}
			this.value = value;
		}
		
		@Override
		public boolean isUndefined() {
			return false;
		}
		
		@Override
		public ScriptString asString() {
			return this;
		}
		
		@Override
		public ScriptObject asObject() {
			return null;
		}
		
		@Override
		public ScriptNumber asNumber() {
			return new InternalScriptNumber(value);
		}
		
		@Override
		public ScriptArray asArray() {
			return null;
		}
		
		@Override
		public String value() {
			return value.getAsString();
		}
		
		@Override
		public JsonElement toJs() {
			return value;
		}
		
		@Override
		public String toString() {
			return toJs().toString();
		}
	}
	private static final class InternalScriptNumber implements ScriptNumber, JsonConvertingBuilder.Internal {
		private final JsonPrimitive value;
		public InternalScriptNumber(JsonPrimitive value) {
			if (!value.isJsonPrimitive() || value.isJsonNull()) {
				throw new IllegalArgumentException();
			}
			this.value = value;
		}
		
		@Override
		public boolean isUndefined() {
			return false;
		}
		
		@Override
		public ScriptString asString() {
			return new InternalScriptString(value);
		}
		
		@Override
		public ScriptObject asObject() {
			return null;
		}
		
		@Override
		public ScriptNumber asNumber() {
			return this;
		}
		
		@Override
		public ScriptArray asArray() {
			return null;
		}
		
		@Override
		public double value() {
			return value.getAsDouble();
		}
		
		@Override
		public JsonElement toJs() {
			return value;
		}
		
		@Override
		public String toString() {
			return toJs().toString();
		}
	}
	private static final class InternalScriptObject implements ScriptObject, JsonConvertingBuilder.Internal {
		private final JsonObject value;
		public InternalScriptObject(JsonObject value) {
			if (!value.isJsonObject() || value.isJsonNull()) {
				throw new IllegalArgumentException();
			}
			this.value = value;
		}
		
		@Override
		public boolean isUndefined() {
			return false;
		}
		
		@Override
		public ScriptString asString() {
			return null;
		}
		
		@Override
		public ScriptObject asObject() {
			return this;
		}
		
		@Override
		public ScriptNumber asNumber() {
			return null;
		}
		
		@Override
		public ScriptArray asArray() {
			return null;
		}
		
		@Override
		public ScriptElement get(String key) {
			return new InternalScriptElement(value.get(key));
		}
		@Override
		public Iterable<Entry> entries() {
			return Iterables.transform(value.entrySet(), new Function<java.util.Map.Entry<String, JsonElement>, ScriptObject.Entry>() {
				@Override
				public Entry apply(final java.util.Map.Entry<String, JsonElement> input) {
					return new Entry() {
						@Override
						public ScriptElement value() {
							return new InternalScriptElement(input.getValue());
						}
						@Override
						public String key() {
							return input.getKey();
						}
					};
				}
			});
		}
		
		@Override
		public JsonElement toJs() {
			return value;
		}
		
		@Override
		public String toString() {
			return toJs().toString();
		}
	}
	private static final class InternalScriptArray implements ScriptArray, JsonConvertingBuilder.Internal {
		private final JsonArray value;
		public InternalScriptArray(JsonArray value) {
			if (!value.isJsonArray() || value.isJsonNull()) {
				throw new IllegalArgumentException();
			}
			this.value = value;
		}
		
		@Override
		public boolean isUndefined() {
			return false;
		}
		
		@Override
		public ScriptString asString() {
			return null;
		}
		
		@Override
		public ScriptObject asObject() {
			return null;
		}
		
		@Override
		public ScriptNumber asNumber() {
			return null;
		}
		
		@Override
		public ScriptArray asArray() {
			return this;
		}
		
		@Override
		public Iterator<ScriptElement> iterator() {
			return Iterators.transform(value.iterator(), new Function<JsonElement, ScriptElement>() {
				@Override
				public ScriptElement apply(JsonElement input) {
					return new InternalScriptElement(input);
				}
			});
		}
		
		@Override
		public JsonElement toJs() {
			return value;
		}
		
		@Override
		public String toString() {
			return toJs().toString();
		}
	}
	private static final class InternalScriptElement implements ScriptElement, JsonConvertingBuilder.Internal {
		private final JsonElement value;
		public InternalScriptElement(JsonElement value) {
			this.value = value;
		}
		
		@Override
		public boolean isUndefined() {
			if (value == null) {
				return true;
			}
			return false; // value.isJsonNull();
		}
		
		@Override
		public ScriptString asString() {
			return new InternalScriptString(value.getAsJsonPrimitive());
		}
		@Override
		public ScriptObject asObject() {
			return new InternalScriptObject(value.getAsJsonObject());
		}
		@Override
		public ScriptNumber asNumber() {
			return new InternalScriptNumber(value.getAsJsonPrimitive());
		}
		@Override
		public ScriptArray asArray() {
			return new InternalScriptArray(value.getAsJsonArray());
		}
		
		@Override
		public JsonElement toJs() {
			return value;
		}
		
		@Override
		public String toString() {
			JsonElement v = toJs();
			if (v == null) {
				return String.valueOf(null);
			}
			return v.toString();
		}
	}

	@Override
	public ScriptElement toJava(Object o) {
		return new InternalScriptElement((o == null) ? null : new JsonParser().parse((String) o));
	}
	@Override
	public Object fromJava(ScriptElement o) {
		JsonElement js = ((JsonConvertingBuilder.Internal) o).toJs();
		if (js == null) {
			return null;
		}
		return js.toString();
	}
	
	@Override
	public ScriptElement undefined() {
		return new InternalScriptElement(null);
	}
	@Override
	public ScriptElement string(String value) {
		return new InternalScriptString(new JsonPrimitive(value));
	}
	@Override
	public ScriptElement number(double value) {
		return new InternalScriptNumber(new JsonPrimitive(value));
	}
	@Override
	public ScriptObjectBuilder object() {
		return new ScriptObjectBuilder() {
			private final JsonObject o = new JsonObject();
			@Override
			public ScriptObjectBuilder put(String key, ScriptElement value) {
				o.add(key, ((JsonConvertingBuilder.Internal) value).toJs());
				return this;
			}
			
			@Override
			public ScriptObject build() {
				return new InternalScriptObject(o);
			}
		};
	}
	@Override
	public ScriptArrayBuilder array() {
		return new ScriptArrayBuilder() {
			private final JsonArray o = new JsonArray();
			@Override
			public ScriptArrayBuilder add(ScriptElement value) {
				o.add(((JsonConvertingBuilder.Internal) value).toJs());
				return this;
			}
			
			@Override
			public ScriptArray build() {
				return new InternalScriptArray(o);
			}
		};
	}
	
}