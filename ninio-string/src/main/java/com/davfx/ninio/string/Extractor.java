package com.davfx.ninio.string;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;

import com.google.common.collect.Lists;

public final class Extractor {
	private String[] from = new String[0];
	private String[] to = new String[0];
	private Reader on = null;
	private final String found;
	private StringBuilder accumulator = null;
	private final List<StringBuilder> accumulators;
	
	private boolean ignoreWhitespaces = false;

	public Extractor() {
		this(null, new LinkedList<StringBuilder>());
	}
	
	private Extractor(String found, List<StringBuilder> accumulators) {
		this.found = found;
		this.accumulators = accumulators;
	}
	
	
	public Extractor ignoreWhitespaces(boolean ignoreWhitespaces) {
		this.ignoreWhitespaces = ignoreWhitespaces;
		return this;
	}
	
	public Extractor from(String... from) {
		this.from = from;
		return this;
	}
	
	public Extractor to(String... to) {
		this.to = to;
		return this;
	}
	
	public Extractor on(Reader on) {
		this.on = on;
		return this;
	}
	
	// Do not call twice
	public Extractor accumulate() {
		accumulator = new StringBuilder();
		accumulators.add(accumulator);
		return this;
	}
	
	public String found() {
		return found;
	}
	
	public String accumulated() {
		return accumulator.toString();
	}
	
	public String contents() throws IOException {
		accumulate();
		extract();
		return accumulated();
	}
	
	/*%%%%%%%
	public String contents() throws IOException {
		StringBuilder b = null;
		if (from.length == 0) {
			b = new StringBuilder();
		}
		
		int[] iFrom = new int[from.length]; // Filled with zeros
		int[] iTo = new int[to.length]; // Filled with zeros
		while (true) {
			int k = on.read();
			if (k < 0) {
				if (b == null) {
					return null;
				}
				if (to.length == 0) {
					return b.toString();
				}
				return null;
			}
			
			char c = (char) k;
			
			if (b != null) {
				b.append(c);
			}
				
			if (b == null) {
				for (int u = 0; u < iFrom.length; u++) {
					if (c == from[u].charAt(iFrom[u])) {
						iFrom[u]++;
						if (iFrom[u] == from[u].length()) {
							b = new StringBuilder();
							break;
						}
					} else if (iFrom[u] > 0) {
						iFrom[u] = 0;
						if (c == from[u].charAt(iFrom[u])) {
							iFrom[u]++;
							if (iFrom[u] == from[u].length()) {
								b = new StringBuilder();
								break;
							}
						}
					}
				}
			}

			for (int u = 0; u < iTo.length; u++) {
				if (c == to[u].charAt(iTo[u])) {
					iTo[u]++;
					if (iTo[u] == to[u].length()) {
						if (b == null) {
							return null;
						}
						b.delete(b.length() - to[u].length(), b.length());
						return b.toString();
					}
				} else if (iTo[u] > 0) {
					iTo[u] = 0;
					if (c == to[u].charAt(iTo[u])) {
						iTo[u]++;
						if (iTo[u] == to[u].length()) {
							if (b == null) {
								return null;
							}
							b.delete(b.length() - to[u].length(), b.length());
							return b.toString();
						}
					}
				}
			}
		}
	}*/
	
	public Extractor extract() throws IOException {
		//%%%%% LOGGER.debug("---> to = {}", Arrays.asList(to));
		
		int[] iFrom = new int[from.length]; // Filled with zeros
		int[] iTo = new int[to.length]; // Filled with zeros
		while (true) {
			int k = on.read();
			if (k < 0) {
				return null;
			}
			
			char c = (char) k;
			
			for (StringBuilder b : accumulators) {
				b.append(c);
			}
			
			if (ignoreWhitespaces && Character.isWhitespace(c)) {
				continue;
			}
			
			for (int u = 0; u < iFrom.length; u++) {
				if (c == from[u].charAt(iFrom[u])) {
					iFrom[u]++;
					if (iFrom[u] == from[u].length()) {
						return new Extractor(from[u], Lists.newLinkedList(accumulators)).on(on).to(to).ignoreWhitespaces(ignoreWhitespaces);
					}
				} else if (iFrom[u] > 0) {
					iFrom[u] = 0;
					if (c == from[u].charAt(iFrom[u])) {
						iFrom[u]++;
						if (iFrom[u] == from[u].length()) {
							return new Extractor(from[u], Lists.newLinkedList(accumulators)).on(on).to(to).ignoreWhitespaces(ignoreWhitespaces);
						}
					}
				}
			}
			
			for (int u = 0; u < iTo.length; u++) {
				if (c == to[u].charAt(iTo[u])) {
					iTo[u]++;
					if (iTo[u] == to[u].length()) {
						if (accumulator != null) {
							accumulator.delete(accumulator.length() - to[u].length(), accumulator.length());
						}
						return null;
					}
				} else if (iTo[u] > 0) {
					iTo[u] = 0;
					if (c == to[u].charAt(iTo[u])) {
						iTo[u]++;
						if (iTo[u] == to[u].length()) {
							if (accumulator != null) {
								accumulator.delete(accumulator.length() - to[u].length(), accumulator.length());
							}
							return null;
						}
					}
				}
			}
		}
	}
}
