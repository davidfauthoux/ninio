package com.davfx.ninio.pubsub;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.v4.ByteArray;
import com.davfx.ninio.core.v4.ByteArrays;
import com.davfx.ninio.pubsub.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.MemoryCache;
import com.google.common.io.BaseEncoding;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.pubnub.api.PNConfiguration;
import com.pubnub.api.PubNub;
import com.pubnub.api.callbacks.PNCallback;
import com.pubnub.api.callbacks.SubscribeCallback;
import com.pubnub.api.enums.PNReconnectionPolicy;
import com.pubnub.api.enums.PNStatusCategory;
import com.pubnub.api.models.consumer.PNPublishResult;
import com.pubnub.api.models.consumer.PNStatus;
import com.pubnub.api.models.consumer.pubsub.PNMessageResult;
import com.pubnub.api.models.consumer.pubsub.PNPresenceEventResult;
import com.typesafe.config.Config;

public final class PubSub implements AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(PubSub.class);

	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(PubSub.class.getPackage().getName());
	private static final int OVERFLOW_MESSAGE_LENGTH = CONFIG.getInt("longmessage.cut");
	private static final double PARTIAL_LONG_MESSAGES_EXPIRATION = ConfigUtils.getDuration(CONFIG, "longmessage.expiration");

	private final PubNub pubnub;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	
	private final SecureRandom random = new SecureRandom();
	private final String senderId = Math.abs(random.nextLong()) + "-" + System.currentTimeMillis() + "-";
	private final AtomicLong nextLongMessageId = new AtomicLong(0L);
	
	public PubSub(String auth) {
		PNConfiguration configuration = new PNConfiguration();
		int i = auth.indexOf("/");
		configuration.setSubscribeKey(auth.substring(0, i));
		configuration.setPublishKey(auth.substring(i + "/".length()));
		configuration.setReconnectionPolicy(PNReconnectionPolicy.LINEAR);
		configuration.setSecure(true);

		pubnub = new PubNub(configuration);
	}
	
	@Override
	public void close() {
		executor.shutdown();
		pubnub.destroy();
		pubnub.forceDestroy();
	}
	
	public CompletableFuture<Void> listen(String channelName, Consumer<ByteArray> consumer) {
		CompletableFuture<Void> future = new CompletableFuture<>();

		pubnub.addListener(new SubscribeCallback() {
			@Override
			public void status(PubNub pubnub, PNStatus status) {
				if (!status.isError()) {
					if (status.getCategory() == PNStatusCategory.PNUnexpectedDisconnectCategory) {
						// This event happens when radio / connectivity is lost
					} else if (status.getCategory() == PNStatusCategory.PNConnectedCategory) {
						// Connect event. You can do stuff like publish, and know you'll get it.
						// Or just use the connected event to confirm you are subscribed for
						// UI / internal notifications, etc
						if (status.getAffectedChannels().contains(channelName)) {
							future.complete(null);
						}
					} else if (status.getCategory() == PNStatusCategory.PNReconnectedCategory) {
						// Happens as part of our regular operation. This event happens when
						// radio / connectivity is lost, then regained.
					} else if (status.getCategory() == PNStatusCategory.PNDecryptionErrorCategory) {
						// Handle messsage decryption error. Probably client configured to
						// encrypt messages and on live data feed it received plain text.
					}
				} else {
					future.completeExceptionally(new Exception(status.toString()));
				}
			}

			private final MemoryCache<String, SortedMap<Integer, byte[]>> partial = MemoryCache.<String, SortedMap<Integer, byte[]>> builder().expireAfterWrite(PARTIAL_LONG_MESSAGES_EXPIRATION).build();
			
			@Override
			public void message(PubNub pubnub, PNMessageResult message) {
				if (message.getChannel() != null) {
					// Message has been received on channel group stored in
					// message.getChannel()
				} else {
					// Message has been received on channel stored in
					// message.getSubscription()
				}

				if (channelName.equals(message.getChannel())) {
					JsonElement m = message.getMessage();
					if (m.isJsonPrimitive()) {
						byte[] messageAsBytes = BaseEncoding.base64().decode(m.getAsString());
						executor.execute(() -> {
							consumer.accept(new ByteArray(new byte[][] {
								messageAsBytes
							}));
						});
					} else {
						JsonObject o = m.getAsJsonObject();
						String id = o.get("id").getAsString();
						int i = o.get("i").getAsInt();
						int n = o.get("n").getAsInt();
						byte[] partialMessageAsBytes = BaseEncoding.base64().decode(o.get("m").getAsString());
						LOGGER.trace("Partial message received {} ({}/{}); {} bytes", id, i, n, partialMessageAsBytes.length);
						if ((i >= 0) && (i < n)) {
							executor.execute(() -> {
								SortedMap<Integer, byte[]> map = partial.get(id);
								if (map == null) {
									map = new TreeMap<>();
									partial.put(id, map);
								}
								map.put(i, partialMessageAsBytes);
								if (map.size() == n) {
									partial.remove(id);
									byte[][] fullMessageAsBytes = new byte[map.size()][];
									for (Map.Entry<Integer, byte[]> e : map.entrySet()) {
										fullMessageAsBytes[e.getKey()] = e.getValue();
									}
									consumer.accept(new ByteArray(fullMessageAsBytes));
								}
							});
						}
					}
				}
			}

			@Override
			public void presence(PubNub pubnub, PNPresenceEventResult presence) {
			}
		});
		
		pubnub.subscribe().channels(Arrays.asList(channelName)).execute();
		
		return future;
	}
	
	public CompletableFuture<Void> post(String channelName, ByteArray messageAsBytes) {
		long totalLength = ByteArrays.totalLength(messageAsBytes);
		if (totalLength < OVERFLOW_MESSAGE_LENGTH) {
			CompletableFuture<Void> future = new CompletableFuture<>();
			byte[] sentBytes = ByteArrays.flattened(messageAsBytes);
			pubnub.publish().channel(channelName).message(new JsonPrimitive(BaseEncoding.base64().encode(sentBytes))).async(new PNCallback<PNPublishResult>() {
				@Override
				public void onResponse(PNPublishResult result, PNStatus status) {
					if (!status.isError()) {
						future.complete(null);
					} else {
						future.completeExceptionally(new Exception(status.toString()));
					}
				}
			});
			return future;
		} else {
			int n = (int) (totalLength / OVERFLOW_MESSAGE_LENGTH);
			if ((totalLength % OVERFLOW_MESSAGE_LENGTH) != 0) {
				n++;
			}
			CompletableFuture<?>[] futures = new CompletableFuture<?>[n];
			long offset = 0L;
			int i = 0;
			JsonElement id = new JsonPrimitive(senderId + nextLongMessageId.getAndIncrement());
			while (offset < totalLength) {
				int l = OVERFLOW_MESSAGE_LENGTH;
				if ((offset + l) > totalLength) {
					l = (int) (totalLength - offset);
				}
				JsonObject o = new JsonObject();
				o.add("id", id);
				o.add("i", new JsonPrimitive(i));
				o.add("n", new JsonPrimitive(n));
				byte[] sentBytes = ByteArrays.cut(messageAsBytes, offset, l);
				o.add("m", new JsonPrimitive(BaseEncoding.base64().encode(sentBytes)));
				CompletableFuture<Void> future = new CompletableFuture<>();
				futures[i] = future;
				LOGGER.trace("Partial message sent {} ({}/{}); {} bytes", id, i, n, l);
				pubnub.publish().channel(channelName).message(o).async(new PNCallback<PNPublishResult>() {
					@Override
					public void onResponse(PNPublishResult result, PNStatus status) {
						if (!status.isError()) {
							future.complete(null);
						} else {
							future.completeExceptionally(new Exception(status.toString()));
						}
					}
				});
				offset += l;
				i++;
			}
			return CompletableFuture.allOf(futures);
		}
	}
}
