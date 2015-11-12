package com.davfx.ninio.csv;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import com.davfx.ninio.csv.impl.CsvKeyedReaderImpl;
import com.davfx.ninio.csv.impl.CsvReaderImpl;
import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class CsvRead {
	
	private static final Config CONFIG = ConfigFactory.load(CsvRead.class.getClassLoader());
	private Charset charset = Charset.forName(CONFIG.getString("ninio.csv.charset"));
	private char delimiter = ConfigUtils.getChar(CONFIG, "ninio.csv.delimiter");
	private char quote = ConfigUtils.getChar(CONFIG, "ninio.csv.quote");
	private boolean ignoreEmptyLines = CONFIG.getBoolean("ninio.csv.ignoreEmptyLines");
	
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

	public MayAutoCloseCsvReader from(final InputStream in) {
		final CsvReader csvReader = new CsvReaderImpl(charset, delimiter, quote, ignoreEmptyLines, in);
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
	
}
