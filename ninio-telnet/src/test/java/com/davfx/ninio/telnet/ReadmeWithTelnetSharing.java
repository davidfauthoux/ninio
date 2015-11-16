package com.davfx.ninio.telnet;

import java.io.IOException;

import com.davfx.ninio.core.Address;

public final class ReadmeWithTelnetSharing {
	
	public static void main(String[] args) throws Exception {
		final String login = "<your-login>";
		final String password = "<your-password>";

		try (TelnetSharing sharing = new TelnetSharing()) {
			TelnetSharingHandler handler = sharing.client(Telnet.sharing(), new Address("127.0.0.1", Telnet.DEFAULT_PORT));
			TelnetSharingHandler.Callback callback = new TelnetSharingHandler.Callback() {
				@Override
				public void failed(IOException e) {
					e.printStackTrace();
				}
				@Override
				public void handle(String response) {
					System.out.println("1--> " + response);
				}
			};
			handler.init(null, "login: ", callback);
			handler.init(login, "Password:", callback);
			handler.init(password, login + "$ ", callback);
			handler.write("ls", login + "$", callback);

			Thread.sleep(2000);
		}
	}
}
