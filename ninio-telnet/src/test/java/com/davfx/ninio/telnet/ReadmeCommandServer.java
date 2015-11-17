package com.davfx.ninio.telnet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.google.common.base.Function;

public final class ReadmeCommandServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(ReadmeCommandServer.class);
	public static void main(String[] args) throws Exception {
		try (Queue queue = new Queue()) {
			try (CommandTelnetServer server = new CommandTelnetServer(queue, new Address("127.0.0.1", 8080), "Alright!", new Function<String, String>() {
				@Override
				public String apply(String input) {
					LOGGER.debug("--> {}", input);
					String result;
					if (input.equals("Hello")) {
						result = "World!";
					} else if (input.equals("Bye")) {
						result = null;
					} else {
						result = "Did you say " + input + "?";
					}
					LOGGER.debug("<-- {}", result);
					return result;
				}
			})) {
				Thread.sleep(1000000);
			}
			Thread.sleep(100);
		}
	}
}
