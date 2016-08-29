package com.davfx.ninio.dns.dependencies;

public final class Dependencies implements com.davfx.ninio.util.Dependencies {
	@Override
	public com.davfx.ninio.util.Dependencies[] dependencies() {
		return new com.davfx.ninio.util.Dependencies[] {
			new com.davfx.ninio.core.dependencies.Dependencies()
		};
	}
}
