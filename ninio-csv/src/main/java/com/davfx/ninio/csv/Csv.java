package com.davfx.ninio.csv;

public final class Csv {
	private Csv() {
	}
	
	public static CsvRead read() {
		return new CsvRead();
	}
	public static CsvWrite write() {
		return new CsvWrite();
	}
}
