package com.davfx.ninio.csv;

import java.io.IOException;

public interface CsvKeyedReader {
	Iterable<String> keys();
	
	interface Line {
		String get(String key);
		int number();
	}
	
	Line next() throws IOException;
}
