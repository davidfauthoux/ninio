package com.davfx.ninio.csv;

public interface MayAutoCloseCsvKeyedReader extends CsvKeyedReader {
	AutoCloseableCsvKeyedReader autoClose();
}
