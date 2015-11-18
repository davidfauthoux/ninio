package com.davfx.ninio.http.util;

import java.io.InputStream;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMultimap;

public interface HttpPost {
	InputStream stream();
	ImmutableMultimap<String, Optional<String>> parameters();
}
