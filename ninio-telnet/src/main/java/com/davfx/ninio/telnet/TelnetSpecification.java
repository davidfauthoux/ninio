package com.davfx.ninio.telnet;

import java.nio.charset.Charset;

import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;

public final class TelnetSpecification {
	private TelnetSpecification() {
	}
	
	private static final Config CONFIG = ConfigUtils.load(TelnetSpecification.class);
	public static final Charset CHARSET = Charset.forName(CONFIG.getString("charset"));
	public static final String EOL = "\r\n";
	public static final int DEFAULT_PORT = 23;
}
