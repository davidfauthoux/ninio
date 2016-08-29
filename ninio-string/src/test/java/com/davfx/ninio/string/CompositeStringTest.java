package com.davfx.ninio.string;

import java.util.Arrays;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.string.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;

public class CompositeStringTest {
	static {
		ConfigUtils.load(new Dependencies(), CompositeStringTest.class.getPackage().getName());
	}
	
	@Test
	public void testHuawei() throws Exception {
		CompositeString.SubCompositeString c = new CompositeString("/", ":", ",", "=", true).on("eNB_666/Cell:a=aa,b=bb/TRX:c=cc");
		Assertions.assertThat(c.prefix()).isEqualTo("eNB_666");
		Assertions.assertThat(c.specification()).isEqualTo(Arrays.asList("Cell", "TRX"));
		Assertions.assertThat(c.value("a")).isEqualTo("aa");
		Assertions.assertThat(c.value("b")).isEqualTo("bb");
		Assertions.assertThat(c.value("c")).isEqualTo("cc");

		Assertions.assertThat(c.next().specification()).isEqualTo("Cell");
		Assertions.assertThat(c.next().suffix().value("a")).isEqualTo("aa");

		Assertions.assertThat(c.next().suffix().next().specification()).isEqualTo("TRX");
		Assertions.assertThat(c.next().suffix().next().suffix().value("c")).isEqualTo("cc");

		Assertions.assertThat(c.next().suffix().next().suffix().next()).isNull();
	}

	@Test
	public void testNSN() throws Exception {
		CompositeString.SubCompositeString c = new CompositeString(null, null, "/", "-", true).on("PLMN-PLMN/NDB-666/CEL-777");
		Assertions.assertThat(c.prefix()).isNull();
		Assertions.assertThat(c.specification()).isEqualTo(Arrays.asList((String) null));
		Assertions.assertThat(c.value("PLMN")).isEqualTo("PLMN");
		Assertions.assertThat(c.value("NDB")).isEqualTo("666");
		Assertions.assertThat(c.value("CEL")).isEqualTo("777");

		Assertions.assertThat(c.next().specification()).isNull();
		Assertions.assertThat(c.next().suffix().next()).isNull();
	}
}
