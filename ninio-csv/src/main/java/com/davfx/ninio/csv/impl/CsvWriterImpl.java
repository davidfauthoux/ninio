package com.davfx.ninio.csv.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;

import com.davfx.ninio.csv.CsvWriter;

public final class CsvWriterImpl implements CsvWriter {
	private static String encode(String s) {
		return s.replace("\"", "\"\"");
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
					writer.write(encode(s));
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
	