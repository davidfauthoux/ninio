package com.davfx.ninio.ping.util;

import java.io.IOException;

import com.davfx.ninio.ping.PingClient;
import com.davfx.ninio.ping.PingClientHandler;
import com.davfx.ninio.ping.PingableAddress;

public final class SimplePingClient {
	private final PingClient client;
	public SimplePingClient(PingClient client) {
		this.client = client;
	}
	
	public void connect(SimplePingClientHandler clientHandler) {
		client.connect(new PingClientHandler() {
			@Override
			public void failed(IOException e) {
				clientHandler.failed(e);
			}
			@Override
			public void close() {
				clientHandler.close();
			}
			@Override
			public void launched(PingClientHandler.Callback callback) {
				clientHandler.launched(new SimplePingClientHandler.Callback() {
					@Override
					public void close() {
						callback.close();
					}
					@Override
					public void ping(String host, double timeout, SimplePingClientHandler.Callback.PingCallback c) {
						PingableAddress a;
						try {
							a = PingableAddress.from(host);
						} catch (IOException ioe) {
							c.failed(ioe);
							return;
						}
						callback.ping(a, 1, 0d, timeout, new PingClientHandler.Callback.PingCallback() {
							@Override
							public void failed(IOException e) {
								c.failed(e);
							}
							@Override
							public void pong(int[] statuses, double[] times) {
								if (statuses[0] == PingClientHandler.VALID_STATUS) {
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
