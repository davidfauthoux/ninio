package com.davfx.ninio.pubsub;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.v4.ByteArrays;
import com.davfx.ninio.pubsub.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.Wait;

public class PubsubTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PubsubTest.class);
	
	static {
		ConfigUtils.load(new Dependencies(), PubsubTest.class.getPackage().getName());
	}
	
	// https://admin.pubnub.com/#/user/443933/account/443897/app/35199489/key/450635/
	// login with Google: david.fauthoux@gmail.com
	private static final String auth = "sub-c-36c320b0-47d1-11e8-bbeb-7ee8620f465d/pub-c-1d437fb3-644d-48c5-8d8c-d748c9afd232";
	
	@Test
	public void testWithShortMessage() throws Exception {
		String posted = "hello";
		Wait wait = new Wait();
		try (PubSub ps = new PubSub(auth)) {
			String channel = "c";
				
			ps.listen(channel, (m) -> {
				String received = ByteArrays.utf8(m);
				LOGGER.debug("Received from {}: {}", channel, received);
				Assertions.assertThat(received).isEqualTo(posted);
				wait.run();
			}).thenRun(() -> {
				LOGGER.debug("Listening {}", channel);
				ps.post(channel, ByteArrays.utf8(posted)).thenRun(() -> {
					LOGGER.debug("Posted to {}: {}", channel, posted);
				});
			});

			wait.waitFor();
		}
	}

	@Test
	public void testWithLongMessage() throws Exception {
		String posted = "hellohellohello"; // Long size, see conf
		Wait wait = new Wait();
		try (PubSub ps = new PubSub(auth)) {
			String channel = "c";
				
			ps.listen(channel, (m) -> {
				String received = ByteArrays.utf8(m);
				LOGGER.debug("Received from {}: {}", channel, received);
				Assertions.assertThat(received).isEqualTo(posted);
				wait.run();
			}).thenRun(() -> {
				LOGGER.debug("Listening {}", channel);
				ps.post(channel, ByteArrays.utf8(posted)).thenRun(() -> {
					LOGGER.debug("Posted to {}: {}", channel, posted);
				});
			});

			wait.waitFor();
		}
	}
}
