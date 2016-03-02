package com.davfx.ninio.ssh;

import java.io.IOException;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.SocketReadyFactory;
import com.davfx.ninio.telnet.TelnetSharing;
import com.davfx.ninio.telnet.TelnetSharingHandler;

public final class ReadmeWithSshSharing {
	
	public static void main(String[] args) throws Exception {
		final String login = "<your-login>";
		final String password = "<your-password>";

		try (Queue queue = new Queue()) {
			try (TelnetSharing sharing = new TelnetSharing(queue, new SocketReadyFactory(queue))) {
				TelnetSharingHandler handler = sharing.client(Ssh.sharing(login, password), new Address("127.0.0.1", Ssh.DEFAULT_PORT));
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
				handler.init(null, login + "$ ", callback);
				handler.write("ls", login + "$ ", callback);
	
				Thread.sleep(10000);
			}
		}
	}
}
