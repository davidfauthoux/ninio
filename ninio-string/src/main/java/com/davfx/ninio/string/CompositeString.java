package com.davfx.ninio.string;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.common.base.Splitter;

public final class CompositeString {
	public interface SubCompositeString {
		String prefix();
		Next next();

		// Composition functions
		String value(String key);
		Iterable<String> specification();
	}
	
	public interface Suffix {
		String value(String key);
		Next next();
	}
	
	public interface Next {
		String specification();
		Suffix suffix();
	}
	
	private final String globalElementSeparator;
	private final String specificationSeparator;
	private final String keyValueListSeparator;
	private final String keyValueSeparator;
	private final boolean trim;

	public CompositeString(String globalElementSeparator, String specificationSeparator, String keyValueListSeparator, String keyValueSeparator, boolean trim) {
		this.globalElementSeparator = globalElementSeparator;
		this.specificationSeparator = specificationSeparator;
		this.keyValueListSeparator = keyValueListSeparator;
		this.keyValueSeparator = keyValueSeparator;
		this.trim = trim;
	}

	public SubCompositeString on(String s) {
		return new InnerSubCompositeString(globalElementSeparator, specificationSeparator, keyValueListSeparator, keyValueSeparator, trim, s);
	}
	
	private static final class InnerSubCompositeString implements SubCompositeString {
		private final int firstSlash;
		private final String prefix;
		private final Next next;

		private InnerSubCompositeString(String globalElementSeparator, String specificationSeparator, String keyValueListSeparator, String keyValueSeparator, boolean trim, String s) {
			if (globalElementSeparator == null) {
				firstSlash = -1;
			} else {
				firstSlash = s.indexOf(globalElementSeparator);
			}
			if (firstSlash < 0) {
				prefix = null;
				next = new InnerNext(globalElementSeparator, specificationSeparator, keyValueListSeparator, keyValueSeparator, trim, s);
			} else {
				prefix = s.substring(0, firstSlash);
				next = new InnerNext(globalElementSeparator, specificationSeparator, keyValueListSeparator, keyValueSeparator, trim, s.substring(firstSlash + 1));
			}
		}
		
		@Override
		public String prefix() {
			return prefix;
		}

		@Override
		public Next next() {
			return next;
		}
		
		@Override
		public Iterable<String> specification() {
			List<String> s = new LinkedList<>();
			Next n = next;
			while (n != null) {
				s.add(n.specification());
				n = n.suffix().next();
			}
			return s;
		}
		
		@Override
		public String value(String key) {
			Next n = next;
			while (n != null) {
				String v = n.suffix().value(key);
				if (v != null) {
					return v;
				}
				n = n.suffix().next();
			}
			return null;
		}
	}

	private static final class InnerNext implements Next {
		private final int firstColon;
		private final String type;
		private final Suffix suffix;
		private final String s;

		private InnerNext(String globalElementSeparator, String specificationSeparator, String keyValueListSeparator, String keyValueSeparator, boolean trim, String s) {
			this.s = s;
			if (specificationSeparator == null) {
				firstColon = -1;
			} else {
				firstColon = s.indexOf(specificationSeparator);
			}
			if (firstColon < 0) {
				type = null;
				suffix = new InnerSuffix(globalElementSeparator, specificationSeparator, keyValueListSeparator, keyValueSeparator, trim, s);
			} else {
				type = s.substring(0, firstColon);
				suffix = new InnerSuffix(globalElementSeparator, specificationSeparator, keyValueListSeparator, keyValueSeparator, trim, s.substring(firstColon + 1));
			}
		}

		@Override
		public String specification() {
			return type;
		}

		@Override
		public Suffix suffix() {
			return suffix;
		}

		@Override
		public String toString() {
			return s;
		}
	}

	private static final class InnerSuffix implements Suffix {
		private final String keyValueListSeparator;
		private final String keyValueSeparator;
		private final boolean trim;
		private final String s;
		private final int nextColon;
		private final int slashBeforeNextColon;
		private Map<String, String> values = null;
		private final Next next;

		private InnerSuffix(String globalElementSeparator, String specificationSeparator, String keyValueListSeparator, String keyValueSeparator, boolean trim, String s) {
			this.s = s;
			this.keyValueListSeparator = keyValueListSeparator;
			this.keyValueSeparator = keyValueSeparator;
			this.trim = trim;
			if (specificationSeparator == null) {
				nextColon = -1;
			} else {
				nextColon = s.indexOf(specificationSeparator);
			}
			if (nextColon < 0) {
				slashBeforeNextColon = -1;
			} else {
				if (globalElementSeparator == null) {
					slashBeforeNextColon = -1;
				} else {
					slashBeforeNextColon = s.lastIndexOf(globalElementSeparator, nextColon);
				}
			}

			if (slashBeforeNextColon < 0) {
				next = null;
			} else {
				next = new InnerNext(globalElementSeparator, specificationSeparator, keyValueListSeparator, keyValueSeparator, trim, s.substring(slashBeforeNextColon + 1));
			}
		}

		@Override
		public String toString() {
			return s;
		}

		private Map<String, String> split() {
			if (values != null) {
				return values;
			}
			values = new HashMap<String, String>();
			String toSplit;
			if (slashBeforeNextColon < 0) {
				toSplit = s;
			} else {
				toSplit = s.substring(0, slashBeforeNextColon);
			}
			for (String t : Splitter.on(keyValueListSeparator).splitToList(trim ? toSplit.trim() : toSplit)) {
				List<String> kv = Splitter.on(keyValueSeparator).splitToList(trim ? t.trim() : t);
				values.put(kv.get(0), kv.get(1));
			}
			return values;
		}

		@Override
		public String value(String key) {
			return split().get(key);
		}

		@Override
		public Next next() {
			return next;
		}
	}

}
