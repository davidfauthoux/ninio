package com.davfx.ninio.csv;

public interface MayAutoCloseCsvWriter extends CsvWriter {
	AutoCloseableCsvWriter autoClose();
}
