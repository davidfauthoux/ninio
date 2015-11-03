package com.davfx.ninio.csv.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

import com.davfx.ninio.csv.CsvReader;

public final class CsvReaderImpl implements CsvReader {
	private final char delimiter;
	private final char quote;
	private final boolean ignoreEmptyLines;

	private final BufferedReader reader;

	public CsvReaderImpl(Charset charset, char delimiter, char quote, boolean ignoreEmptyLines, InputStream in) {
		this.delimiter = delimiter;
		this.quote = quote;
		this.ignoreEmptyLines = ignoreEmptyLines;
		reader = new BufferedReader(new InputStreamReader(in, charset));
	}
	
	@Override
	public String skip() throws IOException {
		while (true) {
			String line = reader.readLine();
			if (line == null) {
				return null;
			}
			
			line = line.trim();
			if (ignoreEmptyLines && line.isEmpty()) {
				continue;
			}

			return line;
		}
	}
	
	@Override
	public Iterable<String> next() throws IOException {
		while (true) {
			String line = reader.readLine();
			if (line == null) {
				return null;
			}
			
			line = line.trim();
			if (ignoreEmptyLines && line.isEmpty()) {
				continue;
			}

			List<String> components = new LinkedList<String>();
			
			int i = 0;
			while (i < line.length()) {
				if (line.charAt(i) == quote) {
					StringBuilder c = new StringBuilder();
					int n = i + 1;
					while (true) {
						int j = line.indexOf(quote, n);
						if (j < 0) {
							String component = line.substring(n);
							c.append(component).append('\n');
							line = reader.readLine();
							n = 0;
							continue;
						}
						if ((j < (line.length() - 1)) && (line.charAt(j + 1) == quote)) {
							String component = line.substring(n, j);
							c.append(component).append(quote);
							n = j + 2;
							continue;
						} else {
							String component = line.substring(n, j);
							c.append(component);
							components.add(c.toString());
							// Next char is ';'
							i = j + 2;
							break;
						}
					}
				} else {
					int j = line.indexOf(delimiter, i);
					if (j < 0) {
						String component = line.substring(i);
						components.add(component);
						i = line.length();
					} else {
						String component = line.substring(i, j);
						components.add(component);
						i = j + 1;
						if (i == line.length()) {
							components.add("");
						}
					}
				}
			}
			return components;
		}
	}
}
