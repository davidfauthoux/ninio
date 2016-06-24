package com.davfx.ninio.util;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.typesafe.config.Config;

public final class ConfigUtilsTest {
	
	@Test
	public void test() throws Exception {
		Config c = ConfigUtils.load(ConfigUtilsTest.class, "test");
		Assertions.assertThat(c.getString("a.b")).isEqualTo("bb");
	}
	@Test
	public void test2() throws Exception {
		Config c = ConfigUtils.load(ConfigUtilsTest.class, "test2");
		Assertions.assertThat(c.getString("a.b")).isEqualTo("cc");
	}
}
