package com.davfx.ninio.core.dependencies;

public final class Dependencies implements com.davfx.ninio.util.Dependencies {
	@Override
	public com.davfx.ninio.util.Dependencies[] dependencies() {
		return new com.davfx.ninio.util.Dependencies[] {
			new com.davfx.ninio.util.dependencies.Dependencies()
		};
	}
}
