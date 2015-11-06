package com.davfx.ninio.ftp;

import java.io.IOException;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.ftp.FtpClientHandler.Callback.ListCallback;

public final class Readme {
	public static void main(String[] args) throws Exception {
		String login = "<your-login>";
		String password = "<your-password>";

		new Ftp()
			.withLogin(login).withPassword(password)
		.to(new Address("<ftp-host>", Ftp.DEFAULT_PORT)).create().connect(new FtpClientHandler() {
			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
			@Override
			public void authenticationFailed() {
				System.out.println("Authentication failed");
			}
			@Override
			public void close() {
				System.out.println("Closed");
			}
			@Override
			public void launched(Callback callback) {
				callback.list("/", new ListCallback() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
					@Override
					public void handle(Iterable<String> content) {
						System.out.println(content);
					}
					@Override
					public void doesNotExist(String path) {
						System.out.println("Does not exist: " + path);
					}
				});
			}
		});
		
		Thread.sleep(10000);
	}
}
