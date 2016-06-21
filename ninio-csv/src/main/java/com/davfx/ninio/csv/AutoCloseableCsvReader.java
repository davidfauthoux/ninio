package com.davfx.ninio.csv;

import java.io.Closeable;

public interface AutoCloseableCsvReader extends Closeable, CsvReader, AutoCloseable {
}
