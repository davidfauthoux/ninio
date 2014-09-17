package com.davfx.ninio.trash;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.ftp.FtpClient;
import com.davfx.ninio.ftp.FtpClientHandler;

public class TestFtpClient {
	public static void main(String[] args) throws Exception {
		new FtpClient().withHost("ftpperso.free.fr").withLogin("david.fauthoux").withPassword("orod,ove").connect(new FtpClientHandler() {
			@Override
			public void close() {
				System.out.println("CLOSE");
			}
			
			@Override
			public void launched(Callback callback) {
				callback.list("/irit", new Callback.ListCallback() {
					@Override
					public void failed(IOException e) {
						System.out.println("FAILED");
						callback.close();
					}
					
					@Override
					public void handle(Iterable<String> content) {
						System.out.println(" --> " + content);
						final FileChannel out;
						try {
							out = new FileOutputStream(new File("test.dav.jpg")).getChannel();
						} catch (IOException e) {
							callback.close();
							return;
						}
						callback.download("/irit/dav.jpg", new Callback.DownloadCallback() {
							@Override
							public void failed(IOException e) {
								System.out.println("# FAILED");
							}
							
							@Override
							public void close() {
								try {
									out.close();
								} catch (IOException e) {
								}
								System.out.println("# DONE");
								callback.close();
							}
							
							@Override
							public void handle(Address address, ByteBuffer buffer) {
								try {
									out.write(buffer);
								} catch (IOException e) {
								}
							}
							
							@Override
							public void doesNotExist(String path) {
								System.out.println("# DOES NOT EXIST " + path);
							}
						});
					}
					
					@Override
					public void doesNotExist(String path) {
						System.out.println("DOES NOT EXIST " + path);
						callback.close();
					}
				});
			}

			@Override
			public void failed(IOException cause) {
				System.out.println("FAILED");
			}
			
			@Override
			public void authenticationFailed() {
				System.out.println("AUTHENTICATION FAILED");
			}
		});
	}
}
