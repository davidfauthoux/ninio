package com.davfx.ninio.csv;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

import com.davfx.ninio.csv.impl.CsvWriterImpl;
import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class CsvWrite {
	
	private static final Config CONFIG = ConfigFactory.load(CsvWrite.class.getClassLoader());
	private Charset charset = Charset.forName(CONFIG.getString("ninio.csv.charset"));
	private char delimiter = ConfigUtils.getChar(CONFIG, "ninio.csv.delimiter");
	private char quote = ConfigUtils.getChar(CONFIG, "ninio.csv.quote");
	
	public CsvWrite() {
	}
	
	public CsvWrite withDelimiter(char delimiter) {
		this.delimiter = delimiter;
		return this;
	}
	public CsvWrite withQuote(char quote) {
		this.quote = quote;
		return this;
	}
	public CsvWrite withCharset(Charset charset) {
		this.charset = charset;
		return this;
	}

	public MayAutoCloseCsvWriter to(final OutputStream out)  {
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
	
	public AutoCloseableCsvWriter to(File file) throws IOException {
		return to(new FileOutputStream(file)).autoClose();
	}
	public AutoCloseableCsvWriter append(File file) throws IOException {
		return to(new FileOutputStream(file, true)).autoClose();
	}
}
