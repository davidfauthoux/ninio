package com.davfx.ninio.csv;

import java.io.IOException;

public interface CsvReader {
	String skip() throws IOException;
	Iterable<String> next() throws IOException;
}
