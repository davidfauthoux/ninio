package com.davfx.ninio.core.v4;

import java.util.Arrays;

import com.google.common.io.BaseEncoding;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class ByteArray {

	public final byte[][] bytes;
	private int cachedHashCode = Undefineds.HASH_CODE;

	public ByteArray(byte[][] bytes) {
		this.bytes = bytes;
	}

	@Override
	public String toString() {
		JsonArray a = new JsonArray();
		for (int i = 0; i < bytes.length; i++) {
			JsonObject o = new JsonObject();
			o.add("size", new JsonPrimitive(bytes[i].length));
			o.add("data", new JsonPrimitive(BaseEncoding.base64().encode(bytes[i])));
			a.add(o);
		}
		return a.toString();
	}

	@Override
	public final int hashCode() {
		if (cachedHashCode == Undefineds.HASH_CODE) {
			int[] hash = new int[bytes.length];
			for (int i = 0; i < bytes.length; i++) {
				hash[i] = Arrays.hashCode(bytes[i]);
			}
			cachedHashCode = Arrays.hashCode(hash);
		}
		return cachedHashCode;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null) {
			return false;
		}
		if (getClass() != o.getClass()) {
			return false;
		}
		ByteArray that = (ByteArray) o;
		if (bytes.length != that.bytes.length) {
			return false;
		}
		for (int i = 0; i < bytes.length; i++) {
			if (!Arrays.equals(bytes[i], that.bytes[i])) {
				return false;
			}
		}
		return true;
	}

}
