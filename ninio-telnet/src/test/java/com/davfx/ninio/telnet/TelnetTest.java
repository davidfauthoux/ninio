package com.davfx.ninio.telnet;

import java.io.IOException;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.util.Lock;
import com.google.common.base.Function;

public class TelnetTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(TelnetTest.class);

	@Test
	public void test() throws Exception {
		int port = 8080;
		try (Queue queue = new Queue()) {
			try (CommandTelnetServer server = new CommandTelnetServer(queue, new Address(Address.LOCALHOST, port), "Tell me: ", TelnetSpecification.EOL, new Function<String, String>() {
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

				Thread.sleep(100);

				final Lock<String, IOException> lock = new Lock<>();
				
				try (TelnetSharing sharing = new TelnetSharing()) {
					TelnetSharingHandler handler = sharing.client(Telnet.sharing(), new Address(Address.LOCALHOST, port));
					handler.init(null, ": ", new TelnetSharingHandler.Callback() {
						@Override
						public void failed(IOException e) {
							lock.fail(e);
						}
						@Override
						public void handle(String response) {
							LOGGER.debug("1/ RECEIVED /" + response + "/");
						}
					});
					handler.init("Hello", "World!", new TelnetSharingHandler.Callback() {
						@Override
						public void failed(IOException e) {
							lock.fail(e);
						}
						@Override
						public void handle(String response) {
							LOGGER.debug("2/ RECEIVED /" + response + "/");
						}
					});
					handler.write("Hello", "World!", new TelnetSharingHandler.Callback() {
						@Override
						public void failed(IOException e) {
							lock.fail(e);
						}
						@Override
						public void handle(String response) {
							LOGGER.debug("3/ RECEIVED /" + response + "/");
						}
					});
					handler.write("Hey", "?", new TelnetSharingHandler.Callback() {
						@Override
						public void failed(IOException e) {
							lock.fail(e);
						}
						@Override
						public void handle(String response) {
							LOGGER.debug("4/ RECEIVED /" + response + "/");
							lock.set(response);
						}
					});

					LOGGER.debug("Waiting for result");
					
					Assertions.assertThat(lock.waitFor()).isEqualTo("Did you say Hey?");
				}
			}
			Thread.sleep(100);
		}
	}
	
	@Test
	public void testSameToCheckClose() throws Exception {
		test();
	}
}
