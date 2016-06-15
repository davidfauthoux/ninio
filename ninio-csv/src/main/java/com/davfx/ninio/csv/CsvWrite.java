package com.davfx.ninio.csv;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;

import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;

public final class CsvWrite {
	
	private static final Config CONFIG = ConfigUtils.load(CsvWrite.class);
	private static final Charset DEFAULT_CHARSET = Charset.forName(CONFIG.getString("charset"));
	private static final char DEFAULT_DELIMITER = ConfigUtils.getChar(CONFIG, "delimiter");
	private static final char DEFAULT_QUOTE = ConfigUtils.getChar(CONFIG, "quote");

	private Charset charset = DEFAULT_CHARSET;
	private char delimiter = DEFAULT_DELIMITER;
	private char quote = DEFAULT_QUOTE;
	
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
	
	private static final class CsvWriterImpl implements CsvWriter {
		private static String encode(char quote, String s) {
			return s.replace(String.valueOf(quote), quote + "" + quote);
		}

		public static final class InnerLine implements Line {
			private final char delimiter;
			private final char quote;
			private final Writer writer;
			private boolean beginning = true;
			
			public InnerLine(char delimiter, char quote, Writer writer) {
				this.writer = writer;
				this.delimiter = delimiter;
				this.quote = quote;
			}

			@Override
			public Line append(String s) throws IOException {
				if (beginning) {
					beginning = false;
				} else {
					writer.write(delimiter);
				}

				if (s != null) {
					boolean wrap = (s.length() > 0) && ((s.indexOf(quote) >= 0) || (s.indexOf(delimiter) >= 0) || (s.indexOf('\n') >= 0));
					if (wrap) {
						writer.write(quote);
						writer.write(encode(quote, s));
						writer.write(quote);
					} else {
						writer.write(s);
					}
				}
				return this;
			}
			
			@Override
			public void close() throws IOException {
				writer.write('\n');
			}
		}

		private final char delimiter;
		private final char quote;
		private final Writer writer;
		
		public CsvWriterImpl(Charset charset, char delimiter, char quote, OutputStream out) {
			this.delimiter = delimiter;
			this.quote = quote;
			writer = new OutputStreamWriter(out, charset);
		}

		@Override
		public Line line() throws IOException {
			return new InnerLine(delimiter, quote, writer);
		}
		
		@Override
		public void flush() throws IOException {
			writer.flush();
		}
	}
}
