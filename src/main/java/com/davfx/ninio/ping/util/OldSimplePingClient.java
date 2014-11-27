package com.davfx.ninio.ping.util;

import java.io.IOException;

import com.davfx.ninio.ping.OldPingClient;
import com.davfx.ninio.ping.OldPingClientHandler;
import com.davfx.ninio.ping.PingableAddress;

@Deprecated
public final class OldSimplePingClient {
	private final OldPingClient client;
	public OldSimplePingClient(OldPingClient client) {
		this.client = client;
	}
	
	public void connect(final OldSimplePingClientHandler clientHandler) {
		client.connect(new OldPingClientHandler() {
			@Override
			public void failed(IOException e) {
				clientHandler.failed(e);
			}
			@Override
			public void close() {
				clientHandler.close();
			}
			@Override
			public void launched(final OldPingClientHandler.Callback callback) {
				clientHandler.launched(new OldSimplePingClientHandler.Callback() {
					@Override
					public void close() {
						callback.close();
					}
					@Override
					public void ping(String host, double timeout, final OldSimplePingClientHandler.Callback.PingCallback c) {
						PingableAddress a;
						try {
							a = PingableAddress.from(host);
						} catch (IOException ioe) {
							c.failed(ioe);
							return;
						}
						callback.ping(a, 1, 0d, timeout, new OldPingClientHandler.Callback.PingCallback() {
							@Override
							public void failed(IOException e) {
								c.failed(e);
							}
							@Override
							public void pong(int[] statuses, double[] times) {
								if (statuses[0] == OldPingClientHandler.VALID_STATUS) {
									c.pong(times[0]);
								} else {
									c.failed(new IOException("Failed with status: " + statuses[0]));
								}
							}
						});
					}
				});
			}
		});
	}
}
