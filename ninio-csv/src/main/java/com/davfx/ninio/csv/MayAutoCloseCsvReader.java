package com.davfx.ninio.csv;

public interface MayAutoCloseCsvReader extends CsvReader {
	AutoCloseableCsvReader autoClose();
}
