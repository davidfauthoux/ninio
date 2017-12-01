package com.davfx.ninio.csv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.davfx.ninio.csv.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

public final class CsvRead {
	
	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(CsvRead.class.getPackage().getName());
	private static final Charset DEFAULT_CHARSET = Charset.forName(CONFIG.getString("charset"));
	private static final char DEFAULT_DELIMITER = ConfigUtils.getChar(CONFIG, "delimiter");
	private static final char DEFAULT_QUOTE = ConfigUtils.getChar(CONFIG, "quote");
	private static final boolean DEFAULT_IGNORE_EMPTY_LINES = CONFIG.getBoolean("ignoreEmptyLines");
	private static final char[] DEFAULT_INVALID_CHARACTERS;
	static {
		List<String> l = CONFIG.getStringList("invalid");
		DEFAULT_INVALID_CHARACTERS = new char[l.size()];
		int i = 0;
		for (String s : l) {
			if (s.length() != 1) {
				throw new ConfigException.BadValue(CONFIG.origin(), "invalid", "only single characters allowed");
			}
			DEFAULT_INVALID_CHARACTERS[i] = s.charAt(0);
			i++;
		}
	}

	private Charset charset = DEFAULT_CHARSET;
	private char delimiter = DEFAULT_DELIMITER;
	private char quote = DEFAULT_QUOTE;
	private boolean ignoreEmptyLines = DEFAULT_IGNORE_EMPTY_LINES;
	private char[] invalidCharacters = DEFAULT_INVALID_CHARACTERS;
	
	public CsvRead() {
	}
	
	public CsvRead withDelimiter(char delimiter) {
		this.delimiter = delimiter;
		return this;
	}
	public CsvRead withQuote(char quote) {
		this.quote = quote;
		return this;
	}
	public CsvRead withCharset(Charset charset) {
		this.charset = charset;
		return this;
	}
	public CsvRead ignoringEmptyLines(boolean ignoreEmptyLines) {
		this.ignoreEmptyLines = ignoreEmptyLines;
		return this;
	}
	public CsvRead withInvalidCharacters(char[] invalidCharacters) {
		this.invalidCharacters = invalidCharacters;
		return this;
	}

	public MayAutoCloseCsvReader from(final InputStream in) {
		final CsvReader csvReader = new CsvReaderImpl(charset, delimiter, quote, ignoreEmptyLines, invalidCharacters, in);
		return new MayAutoCloseCsvReader() {
			@Override
			public String skip() throws IOException {
				return csvReader.skip();
			}
			@Override
			public Iterable<String> next() throws IOException {
				return csvReader.next();
			}
			
			@Override
			public AutoCloseableCsvReader autoClose() {
				return new AutoCloseableCsvReader() {
					@Override
					public String skip() throws IOException {
						return csvReader.skip();
					}
					@Override
					public Iterable<String> next() throws IOException {
						return csvReader.next();
					}
					
					@Override
					public void close() throws IOException {
						in.close();
					}
				};
			}
		};
	}

	public AutoCloseableCsvReader from(File file) throws IOException {
		return from(new FileInputStream(file)).autoClose();
	}

	public MayAutoCloseCsvKeyedReader parse(final MayAutoCloseCsvReader wrappee) throws IOException {
		final CsvKeyedReader csvReader = new CsvKeyedReaderImpl(wrappee);
		return new MayAutoCloseCsvKeyedReader() {
			@Override
			public Iterable<String> keys() {
				return csvReader.keys();
			}
			@Override
			public Line next() throws IOException {
				return csvReader.next();
			}
			
			@Override
			public AutoCloseableCsvKeyedReader autoClose() {
				final AutoCloseableCsvReader autoCloseableWrappee = wrappee.autoClose();
				return new AutoCloseableCsvKeyedReader() {
					@Override
					public Iterable<String> keys() {
						return csvReader.keys();
					}
					@Override
					public Line next() throws IOException {
						return csvReader.next();
					}
					
					@Override
					public void close() throws IOException {
						autoCloseableWrappee.close();
					}
				};
			}
		};
	}

	public AutoCloseableCsvKeyedReader parse(final AutoCloseableCsvReader wrappee) throws IOException {
		final CsvKeyedReader csvReader = new CsvKeyedReaderImpl(wrappee);
		return new AutoCloseableCsvKeyedReader() {
			@Override
			public Iterable<String> keys() {
				return csvReader.keys();
			}
			@Override
			public Line next() throws IOException {
				return csvReader.next();
			}
			
			@Override
			public void close() throws IOException {
				wrappee.close();
			}
		};
	}

	public MayAutoCloseCsvKeyedReader parse(InputStream in) throws IOException {
		return parse(from(in));
	}

	public AutoCloseableCsvKeyedReader parse(File file) throws IOException {
		return parse(from(file));
	}
	
	private static final class CsvReaderImpl implements CsvReader {
		private final char delimiter;
		private final char quote;
		private final boolean ignoreEmptyLines;
		private final char[] invalidCharacters;
		private final boolean eolIsInvalid;

		private final BufferedReader reader;
		
		private int currentLine = 0;

		public CsvReaderImpl(Charset charset, char delimiter, char quote, boolean ignoreEmptyLines, char[] invalidCharacters, InputStream in) {
			this.delimiter = delimiter;
			this.quote = quote;
			this.ignoreEmptyLines = ignoreEmptyLines;
			{ // Get all but '\n' treated as a special character
				char[] invalidChars = new char[invalidCharacters.length];
				boolean isInvalid = false;
				int i = 0;
				for (char invalidCharacter : invalidCharacters) {
					if (invalidCharacter == '\n') {
						isInvalid = true;
					} else {
						invalidChars[i] = invalidCharacter;
						i++;
					}
				}
				this.invalidCharacters = new char[i];
				System.arraycopy(invalidCharacters, 0, this.invalidCharacters, 0, i);
				this.eolIsInvalid = isInvalid;
			}
			
			reader = new BufferedReader(new InputStreamReader(in, charset));
		}
		
		@Override
		public String skip() throws IOException {
			while (true) {
				String line = reader.readLine();
				currentLine++;
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
			boolean inQuote = false;
			StringBuilder c = new StringBuilder();
			List<String> components = new LinkedList<String>();

			while (true) {
				String line = reader.readLine();
				if (line == null) {
					currentLine++;
					return null;
				}
				
				for (char invalidCharacter : invalidCharacters) {
					if (line.indexOf(invalidCharacter) >= 0) {
						throw new IOException("Invalid character found at line #" + currentLine);
					}
				}
				
				if (!inQuote) {
					line = line.trim();
					if (ignoreEmptyLines && line.isEmpty()) {
						currentLine++;
						continue;
					}
				}

				
				char last = delimiter;
				int i = 0;
				while (i < line.length()) {
					char character = line.charAt(i);
					if (inQuote) {
						if (character == quote) {
							inQuote = false;
						} else {
							c.append(character);
						}
					} else {
						if (character == quote) {
							if (last == quote) {
								c.append(quote);
							}
							inQuote = true;
						} else if (character == delimiter) {
							components.add(c.toString());
							c.setLength(0);
						} else {
							c.append(character);
						}
					}
					last = character;
					i++;
				}
				
				if (!inQuote) {
					components.add(c.toString());
					currentLine++;
					break;
				}

				if (inQuote) {
					if (eolIsInvalid) {
						throw new IOException("Invalid character found at line #" + currentLine);
					}
				}
				
				currentLine++;
			}

			return components;
		}
	}
	
	private static final class CsvKeyedReaderImpl implements CsvKeyedReader {
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
}
