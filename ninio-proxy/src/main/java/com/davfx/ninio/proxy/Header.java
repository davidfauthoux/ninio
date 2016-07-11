package com.davfx.ninio.proxy;

import java.util.List;
import java.util.Map;

import com.davfx.ninio.util.StringUtils;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;

public final class Header {
	public final String type;
	public final ImmutableMap<String, String> parameters;
	public Header(String type, ImmutableMap<String, String> parameters) {
		this.type = type;
		this.parameters = parameters;
	}
	public Header(String type) {
		this.type = type;
		parameters = ImmutableMap.of();
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder(StringUtils.escape(type, ' '));
		for (Map.Entry<String, String> e : parameters.entrySet()) {
			b.append(" ").append(StringUtils.escape(e.getKey(), ' ')).append(" ").append(StringUtils.escape(e.getValue(), ' '));
		}
		return b.toString();
	}
	
	public static Header of(String header) {
		List<String> l = Splitter.on(' ').splitToList(header);
		String type = null;
		String key = null;
		ImmutableMap.Builder<String, String> p = ImmutableMap.builder();
		for (String s : l) {
			s = StringUtils.unescape(s, ' ');
			if (type == null) {
				type = s;
			} else if (key == null) {
				key = s;
			} else {
				p.put(key, s);
				key = null;
			}
		}
		return new Header(type, p.build());
	}
}
