package com.davfx.ninio.string;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;

public interface Extraction {
	interface Handler {
		void exit(List<Map<String, String>> keyValuesList);
	}
	
	void run(Reader reader, Handler handler) throws IOException;
}
