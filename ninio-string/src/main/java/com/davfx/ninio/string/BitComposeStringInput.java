package com.davfx.ninio.string;

final class BitComposeStringInput<T> implements StringInput<T> {
	private final StringInput<T> shift;
	private final StringInput<T> wrappeeShift;
	private final StringInput<T> wrappeeNoShift;

	public BitComposeStringInput(StringInput<T> shift, StringInput<T> wrappeeShift, StringInput<T> wrappeeNoShift) {
		this.shift = shift;
		this.wrappeeShift = wrappeeShift;
		this.wrappeeNoShift = wrappeeNoShift;
	}
	@Override
	public String get(T h) {
		String sh = shift.get(h);
		if (sh == null) {
			return null;
		}
		String s = wrappeeShift.get(h);
		if (s == null) {
			return null;
		}
		String n = wrappeeNoShift.get(h);
		if (n == null) {
			return null;
		}
		return String.valueOf((Long.parseLong(s) << Integer.parseInt(sh)) | Long.parseLong(n));
	}
}