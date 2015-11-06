package com.davfx.ninio.ssh;

import java.io.File;
import java.io.IOException;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Failable;

public class Download {
	public static void main(String[] args) throws Exception {
		new Ssh()
			.withLogin("<your-login>")
			.withPassword("<your-password>")
		.to(new Address("127.0.0.1", Ssh.DEFAULT_PORT)).download("todownload.txt", new ToFileFailableCloseableByteBufferHandler(new File("downloaded.txt"), new Failable() {
			@Override
			public void failed(IOException e) {
				if (e == null) {
					System.out.println("Done");
					return;
				}
				e.printStackTrace();
			}
		}));
		
		Thread.sleep(1000);
	}
}
