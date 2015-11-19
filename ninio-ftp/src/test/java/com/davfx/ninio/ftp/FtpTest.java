package com.davfx.ninio.ftp;

import java.io.File;
import java.io.IOException;

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor;
import org.apache.ftpserver.usermanager.impl.PropertiesUserManager;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Failable;
import com.davfx.ninio.ftp.FtpClientHandler.Callback.ListCallback;
import com.davfx.util.Lock;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class FtpTest {

	@Test
	public void test() throws Exception {
		final File f = new File("src/test/resources/downloaded");
		f.deleteOnExit();
		f.delete();

		FtpServerFactory serverFactory = new FtpServerFactory();
		File file = new File("./src/test/resources/users.properties").getAbsoluteFile();
		UserManager uman = new PropertiesUserManager(new ClearTextPasswordEncryptor(), file, "admin");
		serverFactory.setUserManager(uman);

		int port = 8080;
		
		ListenerFactory factory = new ListenerFactory();
		factory.setPort(port);
		serverFactory.addListener("default", factory.createListener());
		FtpServer server = serverFactory.createServer();
		server.start();
		
		Thread.sleep(100);

		{
			final Lock<String, IOException> lock = new Lock<>();
			new Ftp().withLogin("admin").withPassword("admin").to(new Address(Address.LOCALHOST, port)).client().connect(new FtpClientHandler() {
				@Override
				public void failed(IOException e) {
					lock.fail(e);
				}
				@Override
				public void authenticationFailed() {
					lock.fail(new IOException("Authentication failed"));
				}
				@Override
				public void close() {
					lock.fail(new IOException("Closed"));
				}
				@Override
				public void launched(Callback callback) {
					callback.list("/", new ListCallback() {
						@Override
						public void failed(IOException e) {
							lock.fail(e);
						}
						@Override
						public void handle(Iterable<String> content) {
							lock.set(content.toString());
						}
						@Override
						public void doesNotExist(String path) {
							lock.fail(new IOException("Does not exist: " + path));
						}
					});
				}
			});
			
			Assertions.assertThat(lock.waitFor()).isEqualTo("[test-dir, users.properties]");
		}
		{
			final Lock<String, IOException> lock = new Lock<>();
			new Ftp().withLogin("admin").withPassword("admin").to(new Address(Address.LOCALHOST, port)).client().connect(new FtpClientHandler() {
				@Override
				public void failed(IOException e) {
					lock.fail(e);
				}
				@Override
				public void authenticationFailed() {
					lock.fail(new IOException("Authentication failed"));
				}
				@Override
				public void close() {
					lock.fail(new IOException("Closed"));
				}
				@Override
				public void launched(Callback callback) {
					callback.list("/test-dir/", new ListCallback() {
						@Override
						public void failed(IOException e) {
							lock.fail(e);
						}
						@Override
						public void handle(Iterable<String> content) {
							lock.set(content.toString());
						}
						@Override
						public void doesNotExist(String path) {
							lock.fail(new IOException("Does not exist: " + path));
						}
					});
				}
			});
			
			Assertions.assertThat(lock.waitFor()).isEqualTo("[test-file]");
		}
		{
			final Lock<String, IOException> lock = new Lock<>();
			new Ftp().withLogin("admin").withPassword("admin").to(new Address(Address.LOCALHOST, port)).client().connect(new FtpClientHandler() {
				@Override
				public void failed(IOException e) {
					lock.fail(e);
				}
				@Override
				public void authenticationFailed() {
					lock.fail(new IOException("Authentication failed"));
				}
				@Override
				public void close() {
					lock.fail(new IOException("Closed"));
				}
				@Override
				public void launched(Callback callback) {
					callback.download("/test-dir/test-file", new ToFileDownloadCallback(f, new Failable() {
						@Override
						public void failed(IOException e) {
							if (e == null) {
								lock.set("");
							} else {
								lock.fail(e);
							}
						}
					}));
				}
			});
			lock.waitFor();
			
			Assertions.assertThat(Files.readFirstLine(f, Charsets.US_ASCII)).isEqualTo("test");
			f.delete();
		}
		server.stop();
	}
}
