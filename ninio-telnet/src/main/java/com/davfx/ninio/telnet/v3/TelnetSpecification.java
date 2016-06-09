package com.davfx.ninio.telnet.v3;

import java.nio.charset.Charset;

import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class TelnetSpecification {
	private TelnetSpecification() {
	}
	
	private static final Config CONFIG = ConfigUtils.load(TelnetSpecification.class);
	public static final Charset CHARSET = Charset.forName(CONFIG.getString("ninio.telnet.charset"));
	public static final String EOL = "\r\n";
	public static final int DEFAULT_PORT = 23;
}
