package com.davfx.ninio.script;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;

final class NashornConvertingBuilder implements ConvertingBuilder {
	
	public NashornConvertingBuilder() {
	}
	
	@Override
	public String fromJsScript(String parameterName) {
		return ""
				+ "if (" + parameterName + " == undefined) return null;"
				+ "return " + parameterName + ";";
	}
	@Override
	public String toJsScript(String parameterName) {
		return ""
			+ "if (" + parameterName + " == null) return null;"
			+ "if (" + parameterName  +" instanceof java.util.LinkedHashMap) {"
				+ "var a = [];"
				+ "var index = 0;"
				+ "while (true) {"
					+ "var v = " + parameterName + "['' + index];"
					+ "if (!v) {"
						+ "break;"
					+ "}"
					+ "a.push(v);"
					+ "index++;"
				+ "}"
				+ "return a;"
			+ "} else {"
				+ "return " + parameterName + ";"
			+ "}";
	}
	
	private static interface Internal {
		Object toJs();
	}
	
	private static final class InternalScriptString implements ScriptString, NashornConvertingBuilder.Internal {
		private final String value;
		public InternalScriptString(String value) {
			if (value == null) {
				throw new NullPointerException();
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
			return new InternalScriptNumber(Double.parseDouble(value));
		}
		
		@Override
		public ScriptArray asArray() {
			return null;
		}
		
		@Override
		public String value() {
			return value;
		}
		
		@Override
		public Object toJs() {
			return value;
		}
		
		@Override
		public String toString() {
			return toJs().toString();
		}
	}
	private static final class InternalScriptNumber implements ScriptNumber, NashornConvertingBuilder.Internal {
		private final double value;
		public InternalScriptNumber(double value) {
			this.value = value;
		}
		
		@Override
		public boolean isUndefined() {
			return false;
		}
		
		@Override
		public ScriptString asString() {
			return new InternalScriptString(Double.toString(value));
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
			return value;
		}
		
		@Override
		public Object toJs() {
			return value;
		}
		
		@Override
		public String toString() {
			return toJs().toString();
		}
	}
	private static final class InternalScriptObject implements ScriptObject, NashornConvertingBuilder.Internal {
		private final Map<String, Object> value;
		public InternalScriptObject(Map<String, Object> value) {
			if (value == null) {
				throw new NullPointerException();
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
			return Iterables.transform(value.entrySet(), new Function<java.util.Map.Entry<String, Object>, ScriptObject.Entry>() {
				@Override
				public Entry apply(final java.util.Map.Entry<String, Object> input) {
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
		public Object toJs() {
			return value;
		}
		
		@Override
		public String toString() {
			return toJs().toString();
		}
	}
	private static final class InternalScriptArray implements ScriptArray, NashornConvertingBuilder.Internal {
		private final Map<String, Object> value;
		public InternalScriptArray(Map<String, Object> value) {
			if (value == null) {
				throw new NullPointerException();
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
			return Iterators.filter(Iterators.transform(value.entrySet().iterator(), new Function<java.util.Map.Entry<String, Object>, ScriptElement>() {
				private int index = 0;
				@Override
				public ScriptElement apply(java.util.Map.Entry<String, Object> input) {
					String key = input.getKey();
					if (key.equals(String.valueOf(index))) {
						index++;
						return new InternalScriptElement(input.getValue());
					}
					return null;
				}
			}), new Predicate<ScriptElement>() {
				@Override
				public boolean apply(ScriptElement input) {
					return (input != null);
				}
			});
		}
		
		@Override
		public Object toJs() {
			return value;
		}
		
		@Override
		public String toString() {
			return toJs().toString();
		}
	}
	private static final class InternalScriptElement implements ScriptElement, NashornConvertingBuilder.Internal {
		private final Object value;
		public InternalScriptElement(Object value) {
			this.value = value;
		}
		
		@Override
		public boolean isUndefined() {
			return (value == null);
		}
		
		@Override
		public ScriptString asString() {
			return new InternalScriptString(value.toString());
		}
		@SuppressWarnings("unchecked")
		@Override
		public ScriptObject asObject() {
			return new InternalScriptObject((Map<String, Object>) value);
		}
		@Override
		public ScriptNumber asNumber() {
			if (value instanceof Number) {
				return new InternalScriptNumber(((Number) value).doubleValue());
			} else {
				return new InternalScriptNumber(Double.parseDouble(value.toString()));
			}
		}
		@SuppressWarnings("unchecked")
		@Override
		public ScriptArray asArray() {
			return new InternalScriptArray((Map<String, Object>) value);
		}
		
		@Override
		public Object toJs() {
			return value;
		}
		
		@Override
		public String toString() {
			Object v = toJs();
			if (v == null) {
				return String.valueOf(null);
			}
			return v.toString();
		}
	}

	@Override
	public ScriptElement toJava(Object o) {
		return new InternalScriptElement(o);
	}
	@Override
	public Object fromJava(ScriptElement o) {
		return ((NashornConvertingBuilder.Internal) o).toJs();
	}
	
	@Override
	public ScriptElement undefined() {
		return new InternalScriptElement(null);
	}
	@Override
	public ScriptElement string(String value) {
		return new InternalScriptString(value);
	}
	@Override
	public ScriptElement number(double value) {
		return new InternalScriptNumber(value);
	}
	@Override
	public ScriptObjectBuilder object() {
		return new ScriptObjectBuilder() {
			private final Map<String, Object> o = new HashMap<>();
			@Override
			public ScriptObjectBuilder put(String key, ScriptElement value) {
				o.put(key, ((NashornConvertingBuilder.Internal) value).toJs());
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
			private final Map<String, Object> o = new LinkedHashMap<>();
			@Override
			public ScriptArrayBuilder add(ScriptElement value) {
				o.put(String.valueOf(o.size()), ((NashornConvertingBuilder.Internal) value).toJs());
				return this;
			}
			
			@Override
			public ScriptArray build() {
				return new InternalScriptArray(o);
			}
		};
	}
	
}