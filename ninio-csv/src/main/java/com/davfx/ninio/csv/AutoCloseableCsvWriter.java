package com.davfx.ninio.csv;

import java.io.Closeable;

public interface AutoCloseableCsvWriter extends Closeable, CsvWriter, AutoCloseable {
}
