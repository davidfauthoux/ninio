package com.davfx.ninio.core.v4;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Ports;
import com.google.common.base.Charsets;

public class TcpSocketTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(TcpSocketTest.class);

	@Test
	public void test() throws Exception {
		Ninio ninio = new Ninio();
		
		String message = "helloworld";
		
		CompletableFuture<Void> waitForClose = new CompletableFuture<>();
		CompletableFuture<String> waitForRead = new CompletableFuture<>();

		ServerSocket ss = new ServerSocket(0);
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Socket s = ss.accept();
					try {
						byte[] b = new byte[message.length()];
						try (OutputStream out = s.getOutputStream()) {
							try (DataInputStream in = new DataInputStream(s.getInputStream())) {
								in.readFully(b);
								LOGGER.debug("Writing back: {}", new String(b, Charsets.UTF_8));
								out.write(b);
								out.flush();
								int r = in.read();
								Assertions.assertThat(r).isEqualTo(-1);
								waitForClose.complete(null);
							}
						}
					} finally {
						s.close();
					}
				} catch (IOException ioe) {
				}
			}
		});
		t.start();
		
		try {
			int port = ss.getLocalPort();
			
			TcpSocket s = new TcpSocket(ninio);
			
			s
			.bind(new Address(Address.ANY, Ports.free(Address.ANY)))
			.connect(new Address(Address.LOCALHOST, port))
			.thenRun(() -> {
				s
				.write(ByteArrays.utf8(message))
				.thenRun(() -> { // Back-pressure test
					try {
						Thread.sleep(300);
					} catch (Exception ie) {
					}
				})
				.thenRun(() -> {
					MutableByteArray buffer = MutableByteArrays.allocate(message.length());

					s
					.read(buffer)
					.thenRun(() -> {
						String r = ByteArrays.utf8(buffer.toByteArray());
						LOGGER.debug("Read: {}", r);
						waitForRead.complete(r);
						s.close();
					});
				});
			})
			.exceptionally(ex -> {
				waitForRead.completeExceptionally(ex);
				return null;
			});

			waitForClose.get();
			Assertions.assertThat(waitForRead.get()).isEqualTo(message);
			
		} finally {
			ss.close();
		}
	}

	/* Should write twice "Starting a new queue thread"
	@Test
	public void testQueues() throws Exception {
		test();
		Thread.sleep(1000);
		test();
	}
	*/
}
