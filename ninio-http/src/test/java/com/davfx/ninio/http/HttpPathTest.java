package com.davfx.ninio.http;

import java.util.Iterator;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.google.common.base.Optional;

public class HttpPathTest {
	@Test
	public void test() {
		HttpPath p = new HttpPath("/a/b/c?k=v&kk=vv&kk=vv2#skip");
		Assertions.assertThat(p.path.toString()).isEqualTo("/a/b/c");
		Assertions.assertThat(p.parameters.get("k").iterator().next().get()).isEqualTo("v");
		Assertions.assertThat(p.parameters.get("kk").iterator().next().get()).isEqualTo("vv");
		Iterator<Optional<String>> it = p.parameters.get("kk").iterator();
		Assertions.assertThat(it.next().get()).isEqualTo("vv");
		Assertions.assertThat(it.next().get()).isEqualTo("vv2");

		p = new HttpPath("/a?k=v&kk=vv#skip");
		Assertions.assertThat(p.path.toString()).isEqualTo("/a");
		Assertions.assertThat(p.parameters.get("k").iterator().next().get()).isEqualTo("v");
		Assertions.assertThat(p.parameters.get("kk").iterator().next().get()).isEqualTo("vv");

		p = new HttpPath("/?k=v&kk=vv#skip");
		Assertions.assertThat(p.path.toString()).isEqualTo("/");
		Assertions.assertThat(p.parameters.get("k").iterator().next().get()).isEqualTo("v");
		Assertions.assertThat(p.parameters.get("kk").iterator().next().get()).isEqualTo("vv");
	}
}
