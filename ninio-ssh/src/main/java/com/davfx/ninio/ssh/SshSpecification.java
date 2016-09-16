package com.davfx.ninio.ssh;

import java.nio.charset.Charset;

import com.davfx.ninio.ssh.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.google.common.primitives.Ints;
import com.typesafe.config.Config;

public final class SshSpecification {
	private SshSpecification() {
	}
	
	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(SshSpecification.class.getPackage().getName());
	public static final Charset CHARSET = Charset.forName(CONFIG.getString("charset"));
	public static final String EOL = "\n";
	public static final int DEFAULT_PORT = 22;

	public static final int OPTIMIZATION_SPACE = Ints.BYTES + 1;
}
