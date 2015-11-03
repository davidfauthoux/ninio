package com.davfx.ninio.csv.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.davfx.ninio.csv.CsvKeyedReader;
import com.davfx.ninio.csv.CsvReader;

public final class CsvKeyedReaderImpl implements CsvKeyedReader {
	private final CsvReader csvReader;
	private final List<String> keys = new ArrayList<String>();
	private int currentNumber = 0;
	
	public CsvKeyedReaderImpl(CsvReader csvReader) throws IOException {
		this.csvReader = csvReader;
		
		Iterable<String> n = csvReader.next();
		if (n == null) {
			throw new IOException("Missing keys header");
		}
		for (String key : n) {
			keys.add(key);
		}
	}
	
	@Override
	public Iterable<String> keys() {
		return keys;
	}
	
	private static final class InnerLine implements Line {
		private final int number;
		private final Map<String, String> values = new LinkedHashMap<String, String>();
		private InnerLine(List<String> keys, int number, Iterable<String> line) {
			this.number = number;
			int index = 0;
			for (String value : line) {
				if (index == keys.size()) {
					break;
				}
				String key = keys.get(index);
				values.put(key, value);
				index++;
			}
		}
		@Override
		public String get(String key) {
			return values.get(key);
		}
		@Override
		public int number() {
			return number;
		}
		@Override
		public String toString() {
			return "#" + (number + 1) + ":" + values;
		}
	}

	@Override
	public Line next() throws IOException {
		Iterable<String> line = csvReader.next();
		if (line == null) {
			return null;
		}
		int n = currentNumber;
		currentNumber++;
		return new InnerLine(keys, n, line);
	}
}
