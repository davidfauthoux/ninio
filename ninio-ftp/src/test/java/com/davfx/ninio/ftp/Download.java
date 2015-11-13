package com.davfx.ninio.ftp;

import java.io.File;
import java.io.IOException;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Failable;

public final class Download {
	public static void main(String[] args) throws Exception {
		String login = "<your-login>";
		String password = "<your-password>";

		new Ftp()
			.withLogin(login).withPassword(password)
		.to(new Address("<ftp-host>", Ftp.DEFAULT_PORT)).client().connect(new FtpClientHandler() {
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
				callback.download("/todownload.txt", new ToFileDownloadCallback(new File("downloaded.txt"), new Failable() {
					@Override
					public void failed(IOException e) {
						if (e == null) {
							System.out.println("Done");
							return;
						}
						e.printStackTrace();
					}
				}));
			}
		});
		
		Thread.sleep(10000);
	}
}
