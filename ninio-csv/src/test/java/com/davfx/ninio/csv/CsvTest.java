package com.davfx.ninio.csv;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import org.assertj.core.api.Assertions;
import org.assertj.core.util.Files;
import org.junit.Test;

import com.davfx.ninio.csv.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.google.common.base.Charsets;

public class CsvTest {

	static {
		ConfigUtils.load(new Dependencies(), CsvTest.class.getPackage().getName());
	}
	
	@Test
	public void test() throws Exception {
		File dir = new File("test-csv");
		Files.delete(dir);
		
		dir.mkdirs();
		File file = new File(dir, "test.csv");
		
		try (AutoCloseableCsvWriter csv = Csv.write().to(file)) {
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

		try (AutoCloseableCsvReader csv = Csv.read().from(file)) {
			Assertions.assertThat(csv.next().toString()).isEqualTo("[a0, a1, a2]");
			for (int i = 0; i < 2; i++) {
				Assertions.assertThat(csv.next().toString()).isEqualTo("[b, \", ,]");
			}
			Assertions.assertThat(csv.next()).isNull();
		}
		
		try (AutoCloseableCsvKeyedReader csv = Csv.read().parse(file)) {
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

	@Test
	public void testWithHeader() throws Exception {
		File dir = new File("test-csv");
		Files.delete(dir);
		
		dir.mkdirs();
		File file = new File(dir, "test.csv");
		
		try (OutputStream out = new FileOutputStream(file)) {
			out.write("HEADER--\n".getBytes(Charsets.UTF_8));
			out.write("HEADER--\n".getBytes(Charsets.UTF_8));
			out.write("HEADER--\n".getBytes(Charsets.UTF_8));
			out.write("\n".getBytes(Charsets.UTF_8));
			
			CsvWriter csv = Csv.write().to(out);
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
			csv.flush();
		}
		
		try (AutoCloseableCsvReader csv = Csv.read().ignoringEmptyLines(true).from(file)) {
			Assertions.assertThat(csv.skip()).isEqualTo("HEADER--");
			Assertions.assertThat(csv.skip()).isEqualTo("HEADER--");
			Assertions.assertThat(csv.skip()).isEqualTo("HEADER--");
			
			CsvKeyedReader csvKeyedReader = Csv.read().parse(csv);
			for (int i = 0; i < 2; i++) {
				CsvKeyedReader.Line line = csvKeyedReader.next();
				Assertions.assertThat(line.get("a0")).isEqualTo("b");
				Assertions.assertThat(line.get("a1")).isEqualTo("\"");
				Assertions.assertThat(line.get("a2")).isEqualTo(",");
			}
			Assertions.assertThat(csv.next()).isNull();
		}
		
		Files.delete(dir);
	}
}
