package com.davfx.ninio.core.v4;

import java.util.concurrent.CompletableFuture;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Ports;

public class TcpSocketServerTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(TcpSocketServerTest.class);

	private static interface WayOfClosing {
		void doClose(TcpSocket socket);
	}
	
	private static void test(WayOfClosing wayOfClosing) throws Exception {
		Ninio ninio = new Ninio();
		
		String message = "helloworld";

		CompletableFuture<Void> waitForClose = new CompletableFuture<>();
		CompletableFuture<String> waitForRead = new CompletableFuture<>();
		
		int port = Ports.free(Address.ANY);
		
		TcpSocketServer ss = new TcpSocketServer(ninio);
		
		ss
		.bind(new Address(Address.ANY, port))
		.listen().thenRun(() -> {
			ss
			.accept()
			.thenAccept((a) -> {
				MutableByteArray buffer = MutableByteArrays.allocate(message.length());

				LOGGER.debug("Accepted");

				a
				.read(buffer)
				.thenRun(() -> {
					ByteArray rb = buffer.toByteArray();
					String r = ByteArrays.utf8(rb);
					LOGGER.debug("Read and write: {}", r);
					a.write(rb);
					
					a
					.read(MutableByteArrays.empty())
					.thenRun(() -> {
						LOGGER.debug("Client socket closed");
						waitForClose.complete(null);
						ss.close();
					}).exceptionally(ex -> {
						waitForClose.completeExceptionally(ex);
						return null;
					});
				});
			});
		})
		.exceptionally(ex -> {
			waitForRead.completeExceptionally(ex);
			return null;
		});
		
		try {
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
						LOGGER.debug("Read (will close): {}", r);
						waitForRead.complete(r);
						wayOfClosing.doClose(s);
					})
					.exceptionally(ex -> {
						waitForRead.completeExceptionally(ex);
						return null;
					});
				});
			})
			.exceptionally(ex -> {
				waitForRead.completeExceptionally(ex);
				return null;
			});

			Assertions.assertThat(waitForRead.get()).isEqualTo(message);
			waitForClose.get();
		} finally {
			ss.close();
		}
	}
	
	@Test
	public void testAbruptlyClosing() throws Exception {
		test(new WayOfClosing() {
			@Override
			public void doClose(TcpSocket socket) {
				socket.close();
			}
		});
	}
	@Test
	public void testGracefullyClosing() throws Exception {
		test(new WayOfClosing() {
			@Override
			public void doClose(TcpSocket socket) {
				socket.write(null);
			}
		});
	}
}
