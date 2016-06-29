package com.davfx.ninio.util;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class StringUtilsTest {
	private static void test(String s) {
		Assertions.assertThat(StringUtils.unescape(StringUtils.escape(s, ' '), ' ')).isEqualTo(s);
	}

	@Test
	public void test() throws Exception {
		test(" ");
		test(" \\");
		test(" \\\\");
		test(" \\\\ ");
		test(" \\e");
		test(" \\b");
		test(" \\e \\b");
		test(" \\e \\b ");
		test(" e b ");
	}
}
