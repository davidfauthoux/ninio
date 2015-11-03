package com.davfx.ninio.csv;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

public interface CsvWriter extends Flushable {
	public interface Line extends AutoCloseable, Closeable {
		Line append(String s) throws IOException;
	}

	Line line() throws IOException;
}
	