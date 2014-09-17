package com.davfx.ninio.trash;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.ssh.SshClient;

public class TestSshClient {
	public static void main(String[] args) throws Exception {
		new SshClient().withHost("172.17.10.31").withLogin("louser").withPassword("pass").connect(new ReadyConnection() {
			@Override
			public void failed(IOException e) {
				System.out.println("FAILED");
			}
			@Override
			public void close() {
				System.out.println("CLOSED");
			}
			
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				System.out.print("-->" + buffer.remaining()+":" + new String(buffer.array(), buffer.position(), buffer.remaining()));
			}

			@Override
			public void connected(FailableCloseableByteBufferHandler write) {
				new Thread(new Runnable() {
					@Override
					public void run() {
						try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in))) {
							while (true) {
								String line = r.readLine();
								if (line == null) {
									break;
								}
								if (line.equals("^C")) {
									break;
								}
								write.handle(null, ByteBuffer.wrap((line + SshClient.EOL).getBytes()));
							}
							write.close();
						} catch (IOException ioe) {
							ioe.printStackTrace();
						}
					}
				}).start();
			}
		});
	}
}
