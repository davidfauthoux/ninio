package com.davfx.ninio.trash;

import java.io.IOException;

import com.davfx.ninio.common.DatagramReadyFactory;
import com.davfx.ninio.ping.PingClient;
import com.davfx.ninio.ping.PingClientConfigurator;
import com.davfx.ninio.ping.PingClientHandler;

public class TestPing {
	public static void main(String[] args) throws Exception {
		//new PingServer(PingClient.DEFAULT_PORT);
		//Thread.sleep(1000);
		new PingClient(new PingClientConfigurator().override(new DatagramReadyFactory())).connect(new PingClientHandler() {
			@Override
			public void failed(IOException e) {
				System.out.println("FAILED");
			}
			@Override
			public void close() {
				System.out.println("CLOSED");
			}
			@Override
			public void launched(final PingClientHandler.Callback callback) {
				callback.ping("google.com", new PingClientHandler.Callback.PingCallback() {
					@Override
					public void failed(IOException e) {
						System.out.println("# FAILED " + e);
						callback.close();
					}
					
					@Override
					public void pong(double time) {
						System.out.println("# PONG " + time);
						callback.close();
					}
				});
			}
		});
	}
}
