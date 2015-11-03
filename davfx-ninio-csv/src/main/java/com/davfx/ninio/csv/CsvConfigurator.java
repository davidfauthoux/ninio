package com.davfx.ninio.csv;

import java.nio.charset.Charset;

import com.google.common.base.Charsets;

public final class CsvConfigurator {
	Charset charset = Charsets.UTF_8;
	char delimiter = ',';
	char quote = '"';
	
	public CsvConfigurator() {
	}
	
	public CsvConfigurator withDelimiter(char delimiter) {
		this.delimiter = delimiter;
		return this;
	}
	public CsvConfigurator withQuote(char quote) {
		this.quote = quote;
		return this;
	}
	public CsvConfigurator withCharset(Charset charset) {
		this.charset = charset;
		return this;
	}

}
