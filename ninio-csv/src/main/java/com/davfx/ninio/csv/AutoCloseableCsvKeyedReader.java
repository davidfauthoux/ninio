package com.davfx.ninio.csv;

import java.io.Closeable;

public interface AutoCloseableCsvKeyedReader extends Closeable, CsvKeyedReader, AutoCloseable {
}
