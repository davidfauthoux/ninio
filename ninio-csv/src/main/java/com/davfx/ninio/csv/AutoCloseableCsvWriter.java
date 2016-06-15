package com.davfx.ninio.csv;

import java.io.Closeable;

public interface AutoCloseableCsvWriter extends AutoCloseable, Closeable, CsvWriter {
}
