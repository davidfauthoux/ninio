package com.davfx.ninio.telnet;

import java.io.IOException;

import com.davfx.ninio.core.Address;

public final class ReadmeWithTelnetSharing {
	public static void main(String[] args) throws Exception {
		final String login = "davidfauthoux"; //"<your-login>";
		final String password = "orod,ove"; //"<your-password>";

		TelnetSharingHandler.Callback callback = new TelnetSharingHandler.Callback() {
			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
			@Override
			public void handle(String response) {
				System.out.println("--> " + response);
			}
		};

		try (TelnetSharing sharing = new TelnetSharing(new Telnet().sharing())) {
			for (int i = 0; i < 2; i++) {
				try (TelnetSharingHandler handler = sharing.client(new Address("127.0.0.1", Telnet.DEFAULT_PORT))) {
					handler.init("login:", null, callback);
					handler.init("Password:", login, callback);
					handler.init(login + "$", password, callback);
					handler.write(login + "$", "ls", callback);
			
					System.out.println("---");

					try (TelnetSharingHandler handler2 = sharing.client(new Address("127.0.0.1", Telnet.DEFAULT_PORT))) {
						handler2.init("login:", null, callback);
						handler2.init("Password:", login, callback);
						handler2.init(login + "$", password, callback);
						handler2.write(login + "$", "echo 'test'", callback);
				
						System.out.println("---");
						Thread.sleep(2000);
					}

					try (TelnetSharingHandler handler2 = sharing.client(new Address("127.0.0.1", Telnet.DEFAULT_PORT))) {
						handler2.init("login:", null, callback);
						handler2.init("Password:", login, callback);
						handler2.init(login + "$", password, callback);
				
						System.out.println("---");
						Thread.sleep(2000);
					}

					Thread.sleep(2000);
				}
				System.out.println("---");
				Thread.sleep(2000);
			}
		}
		Thread.sleep(200000);
	}
}
