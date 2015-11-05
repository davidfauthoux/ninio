package com.davfx.ninio.telnet;

import java.io.IOException;

import com.davfx.ninio.core.Address;

public final class Readme {
	public static void main(String[] args) throws Exception {
		final String login = "<your-login>";
		final String password = "<your-password>";
		
		new Telnet().to(new Address("127.0.0.1", Telnet.DEFAULT_PORT)).create().connect(new TelnetClientHandler() {
			private final StringBuilder received = new StringBuilder();
			private Callback callback;
			private boolean done = false;
			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
			@Override
			public void close() {
				System.out.println("Closed");
			}
			@Override
			public void received(String text) {
				received.append(text);
				System.out.print(text);
				if (received.toString().endsWith("login: ")) {
					received.setLength(0);
					callback.send(login + TelnetClient.EOL);
				}
				if (received.toString().endsWith("Password:")) {
					received.setLength(0);
					callback.send(password + TelnetClient.EOL);
				}
				if (!done && received.toString().endsWith(login + "$ ")) {
					received.setLength(0);
					callback.send("echo TEST" + TelnetClient.EOL);
					done = true;
				}
			}
			@Override
			public void launched(Callback callback) {
				this.callback = callback;
			}
		});
	}
}
