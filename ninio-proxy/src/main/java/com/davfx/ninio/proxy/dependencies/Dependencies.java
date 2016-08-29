package com.davfx.ninio.proxy.dependencies;

public final class Dependencies implements com.davfx.ninio.util.Dependencies {
	@Override
	public com.davfx.ninio.util.Dependencies[] dependencies() {
		return new com.davfx.ninio.util.Dependencies[] {
			new com.davfx.ninio.ping.dependencies.Dependencies(),
			new com.davfx.ninio.http.dependencies.Dependencies()
		};
	}
}
