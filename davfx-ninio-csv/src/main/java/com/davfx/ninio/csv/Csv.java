package com.davfx.ninio.csv;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import com.davfx.ninio.csv.impl.CsvKeyedReaderImpl;
import com.davfx.ninio.csv.impl.CsvReaderImpl;
import com.davfx.ninio.csv.impl.CsvWriterImpl;
import com.google.common.base.Charsets;

public final class Csv {
	private Charset charset = Charsets.UTF_8;
	private char delimiter = ',';
	private char quote = '"';
	
	public Csv() {
	}
	
	public Csv withDelimiter(char delimiter) {
		this.delimiter = delimiter;
		return this;
	}
	public Csv withQuote(char quote) {
		this.quote = quote;
		return this;
	}
	public Csv withCharset(Charset charset) {
		this.charset = charset;
		return this;
	}

	public MayAutoCloseCsvReader read(final InputStream in) {
		final CsvReader csvReader = new CsvReaderImpl(charset, delimiter, quote, in);
		return new MayAutoCloseCsvReader() {
			@Override
			public Iterable<String> next() throws IOException {
				return csvReader.next();
			}
			
			@Override
			public AutoCloseableCsvReader autoClose() {
				return new AutoCloseableCsvReader() {
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

	public AutoCloseableCsvReader read(File file) throws IOException {
		return read(new FileInputStream(file)).autoClose();
	}
	
	public MayAutoCloseCsvKeyedReader parse(final InputStream in) throws IOException {
		final CsvKeyedReader csvReader = new CsvKeyedReaderImpl(read(in));
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
						in.close();
					}
				};
			}
		};
	}

	public AutoCloseableCsvKeyedReader parse(File file) throws IOException {
		return parse(new FileInputStream(file)).autoClose();
	}
	
	public MayAutoCloseCsvWriter write(final OutputStream out)  {
		final CsvWriter csvWriter = new CsvWriterImpl(charset, delimiter, quote, out);
		return new MayAutoCloseCsvWriter() {
			@Override
			public Line line() throws IOException {
				return csvWriter.line();
			}

			@Override
			public void flush() throws IOException {
				csvWriter.flush();
			}
			
			@Override
			public AutoCloseableCsvWriter autoClose() {
				return new AutoCloseableCsvWriter() {
					
					@Override
					public Line line() throws IOException {
						return csvWriter.line();
					}
					
					@Override
					public void flush() throws IOException {
						csvWriter.flush();
					}
					
					@Override
					public void close() throws IOException {
						try {
							csvWriter.flush();
						} finally {
							out.close();
						}
					}
				};
			}
		};
	}
	
	public AutoCloseableCsvWriter write(File file) throws IOException {
		return write(new FileOutputStream(file)).autoClose();
	}
	public AutoCloseableCsvWriter append(File file) throws IOException {
		return write(new FileOutputStream(file, true)).autoClose();
	}
}
