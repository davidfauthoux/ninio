package com.davfx.ninio.string;

final class CompositeValueStringInput<T> implements StringInput<T> {
	private final StringInput<T> globalElementSeparator;
	private final StringInput<T> specificationSeparator;
	private final StringInput<T> keyValueListSeparator;
	private final StringInput<T> keyValueSeparator;
	private final StringInput<T> trim;
	private final StringInput<T> key;
	private final StringInput<T> wrappee;

	public CompositeValueStringInput(StringInput<T> globalElementSeparator, StringInput<T> specificationSeparator, StringInput<T> keyValueListSeparator, StringInput<T> keyValueSeparator, StringInput<T> trim, StringInput<T> key, StringInput<T> wrappee) {
		this.globalElementSeparator = globalElementSeparator;
		this.specificationSeparator = specificationSeparator;
		this.keyValueListSeparator = keyValueListSeparator;
		this.keyValueSeparator = keyValueSeparator;
		this.trim = trim;
		this.key = key;
		this.wrappee = wrappee;
	}
	
	@Override
	public String get(T h) {
		String s = wrappee.get(h);
		if (s == null) {
			return null;
		}
		String ges = globalElementSeparator.get(h);
		String ss = specificationSeparator.get(h);
		String kvls = keyValueListSeparator.get(h);
		String kvs = keyValueSeparator.get(h);
		String t = trim.get(h);
		String k = key.get(h);
		CompositeString.SubCompositeString c = new CompositeString(ges, ss, kvls, kvs, Boolean.parseBoolean(t)).on(s);
		return c.value(k);
	}
}
