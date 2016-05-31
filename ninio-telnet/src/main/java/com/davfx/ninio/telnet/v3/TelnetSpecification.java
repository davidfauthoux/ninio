package com.davfx.ninio.telnet.v3;

import java.nio.charset.Charset;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class TelnetSpecification {
	private TelnetSpecification() {
	}
	
	private static final Config CONFIG = ConfigFactory.load(TelnetSpecification.class.getClassLoader());
	public static final Charset CHARSET = Charset.forName(CONFIG.getString("ninio.telnet.charset"));
	public static final String EOL = "\r\n";
}
