package com.davfx.ninio.csv;

import java.io.IOException;

public interface CsvReader {
	Iterable<String> next() throws IOException;
}
