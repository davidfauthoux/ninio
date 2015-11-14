package com.davfx.ninio.http;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.google.common.collect.ImmutableMultimap;

public class HttpPathTest {
	@Test
	public void test() {
		HttpPath p = new HttpPath("/a/b/c?k=v&kk=vv#skip");
		Assertions.assertThat(p.path).isEqualTo("a/b/c");
		Assertions.assertThat(p.parameters).isEqualTo(ImmutableMultimap.<String, String>of("k", "v", "kk", "vv"));

		p = new HttpPath("/a?k=v&kk=vv#skip");
		Assertions.assertThat(p.path).isEqualTo("a");
		Assertions.assertThat(p.parameters).isEqualTo(ImmutableMultimap.<String, String>of("k", "v", "kk", "vv"));

		p = new HttpPath("/?k=v&kk=vv#skip");
		Assertions.assertThat(p.path).isEqualTo("");
		Assertions.assertThat(p.parameters).isEqualTo(ImmutableMultimap.<String, String>of("k", "v", "kk", "vv"));
	}
}
