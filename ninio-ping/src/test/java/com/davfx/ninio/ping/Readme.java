package com.davfx.ninio.ping;

import java.io.IOException;

import com.davfx.ninio.ping.PingClientHandler.Callback.PingCallback;

public final class Readme {
	public static void main(String[] args) throws Exception {
		new Ping().ping("127.0.0.1", new PingCallback() {
			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
			@Override
			public void pong(double time) {
				System.out.println(time);
			}
		});
		
		Thread.sleep(1000);
	}
}
