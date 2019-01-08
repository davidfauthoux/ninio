package com.davfx.ninio.pubsub;

import java.util.concurrent.ConcurrentLinkedDeque;

import com.davfx.ninio.core.v4.ByteArray;
import com.davfx.ninio.core.v4.ByteArrayConsumer;
import com.davfx.ninio.core.v4.ByteArrayProducer;

public final class Mirror {
	private static final String UPDATE_CHANNEL_PREFIX = "u-";
	
	private final PubSub pubSub;
	private final String uuid;
	private final ConcurrentLinkedDeque<ByteArray> blocks = new ConcurrentLinkedDeque<>();
	
	public Mirror(String auth, String uuid) {
		this.uuid = uuid;
		pubSub = new PubSub(auth);
		pubSub.listen(UPDATE_CHANNEL_PREFIX + uuid, (m) -> {
			ByteArrayConsumer c = new ByteArrayConsumer(m);
			int blockIndex = c.consumeInt();
			ByteArray block = c.consumeByteArray();
		});
	}
	
	public void add(ByteArray block) {
		ByteArrayProducer p = new ByteArrayProducer();
		p.produceInt(blocks.size());
		p.produceByteArray(block);
		pubSub.post(UPDATE_CHANNEL_PREFIX + uuid, block);
		blocks.add(block);
	}
}
