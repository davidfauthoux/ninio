package com.davfx.ninio.util;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class MemoryCacheTest {
	@Test
	public void testExpireAfterWrite() throws Exception {
		MemoryCache<String, String> cache = MemoryCache.<String, String> builder().expireAfterWrite(0.1d).build();
		cache.put("k", "v");
		Assertions.assertThat(cache.get("k")).isEqualTo("v");
		Thread.sleep(50);
		Assertions.assertThat(cache.get("k")).isEqualTo("v");
		Thread.sleep(70);
		Assertions.assertThat(cache.get("k")).isNull();
	}

	@Test
	public void testExpireAfterAccess() throws Exception {
		MemoryCache<String, String> cache = MemoryCache.<String, String> builder().expireAfterAccess(0.1d).build();
		cache.put("k", "v");
		Assertions.assertThat(cache.get("k")).isEqualTo("v");
		Thread.sleep(50);
		Assertions.assertThat(cache.get("k")).isEqualTo("v");
		Thread.sleep(70);
		Assertions.assertThat(cache.get("k")).isEqualTo("v");
		Thread.sleep(150);
		Assertions.assertThat(cache.get("k")).isNull();
	}
}
