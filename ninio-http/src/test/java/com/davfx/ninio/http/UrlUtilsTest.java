package com.davfx.ninio.http;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.google.common.collect.ImmutableMultimap;

public class UrlUtilsTest {
	@Test
	public void test() throws Exception {
		Assertions.assertThat(UrlUtils.merge(ImmutableMultimap.<String, String> of("a", "a1"), ImmutableMultimap.<String, String> of("a", "a2", "a", "a3")).toString()).isEqualTo("{a=[a1, a2, a3]}");
	}
}
