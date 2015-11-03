package com.davfx.ninio.csv;

import java.io.File;

import org.assertj.core.api.Assertions;
import org.assertj.core.util.Files;
import org.junit.Test;

import com.google.common.base.Charsets;

public class TestCsv {

	@Test
	public void test() throws Exception {
		File dir = new File("test-csv");
		Files.delete(dir);
		
		dir.mkdirs();
		File file = new File(dir, "test.csv");
		
		try (AutoCloseableCsvWriter csv = new Csv().write(file)) {
			try (CsvWriter.Line line = csv.line()) {
				line.append("a0");
				line.append("a1");
				line.append("a2");
			}
			for (int i = 0; i < 2; i++) {
				try (CsvWriter.Line line = csv.line()) {
					line.append("b");
					line.append("\"");
					line.append(",");
				}
			}
		}
		
		Assertions.assertThat(Files.contentOf(file, Charsets.UTF_8).replace('\n', '^')).isEqualTo("a0,a1,a2^b,\"\"\"\",\",\"^b,\"\"\"\",\",\"^");

		try (AutoCloseableCsvReader csv = new Csv().read(file)) {
			Assertions.assertThat(csv.next().toString()).isEqualTo("[a0, a1, a2]");
			for (int i = 0; i < 2; i++) {
				Assertions.assertThat(csv.next().toString()).isEqualTo("[b, \", ,]");
			}
			Assertions.assertThat(csv.next()).isNull();
		}
		
		try (AutoCloseableCsvKeyedReader csv = new Csv().parse(file)) {
			for (int i = 0; i < 2; i++) {
				CsvKeyedReader.Line line = csv.next();
				Assertions.assertThat(line.get("a0")).isEqualTo("b");
				Assertions.assertThat(line.get("a1")).isEqualTo("\"");
				Assertions.assertThat(line.get("a2")).isEqualTo(",");
			}
			Assertions.assertThat(csv.next()).isNull();
		}
		
		Files.delete(dir);
	}

}
