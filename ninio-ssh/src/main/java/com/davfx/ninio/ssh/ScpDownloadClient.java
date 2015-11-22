package com.davfx.ninio.ssh;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.ReadyConnection;
import com.google.common.base.Splitter;

public final class ScpDownloadClient {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ScpDownloadClient.class);
	
	private final SshClient client;
	
	public ScpDownloadClient(SshClient client) {
		this.client = client;
	}
	
	public void get(String filePath, final FailableCloseableByteBufferHandler handler) {
		client.exec("scp -f " + filePath.replace(" ", "\\ ")).connect(new ReadyConnection() {
			private CloseableByteBufferHandler write;
			private long size = -1L;
			private long count = 0L;
			private boolean closed = false;
			
			@Override
			public void failed(IOException e) {
				if (closed) {
					return;
				}
				closed = true;
				handler.failed(e);
			}

			@Override
			public void close() {
				if (closed) {
					return;
				}
				closed = true;
				if (count < size) {
					handler.failed(new IOException("Closed before whole file received"));
				} else {
					handler.close();
				}
			}
			
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				if (closed) {
					return;
				}

				if (size >= 0L) {
					if (buffer.remaining() > (size - count)) { // Terminated by \0
						buffer.limit(buffer.position() + ((int) (size - count)));
					}
					count += buffer.remaining();
					handler.handle(address, buffer);
					if (count == size) {
						write.close();
						closed = true;
						handler.close();
					}
				} else {
					String header = new String(buffer.array(), buffer.position(), buffer.remaining());
					LOGGER.trace("Header: {}", header);
					try {
						size = Long.parseLong(Splitter.on(' ').splitToList(header).get(1));
					} catch (Exception e) {
						write.close();
						closed = true;
						handler.failed(new IOException("SCP header corrupted: " + header, e));
					}
					write.handle(null, ByteBuffer.wrap(new byte[] { 0 }));
				}
			}

			@Override
			public void connected(FailableCloseableByteBufferHandler write) {
				this.write = write;
				write.handle(null, ByteBuffer.wrap(new byte[] { 0 }));
			}
		});
	}
	
	/*%% Not working with big files
	public void put(final String filePath, final long fileSize, final ReadyConnection in) {
		client.exec("scp -t " + filePath.replace(" ", "\\ ")).connect(new ReadyConnection() {
			private CloseableByteBufferHandler write;
			private int countAck = 0;
			
			@Override
			public void failed(IOException e) {
				in.failed(e);
			}

			@Override
			public void close() {
				if (write == null) {
					return;
				}
				in.failed(new IOException("Closed by peer"));
			}
			
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				LOGGER.debug("Upload response: {}", new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8));
				if (write == null) {
					return;
				}
				countAck++;
				if (countAck == 3) {
					LOGGER.debug("Upload done: {}", filePath);
					write.close();
					write = null;
					in.close();
				}
			}

			@Override
			public void connected(final FailableCloseableByteBufferHandler write) {
				this.write = write;
				
				String name = filePath;
				int k = name.lastIndexOf('/');
				if (k >= 0) {
					name = name.substring(k + 1);
				}
				name = name.replace(" ", "\\ ");
				
				write.handle(null, ByteBuffer.wrap(("C0644 " + fileSize + " " + name + SshClient.EOL).getBytes(Charsets.UTF_8)));
				in.connected(new FailableCloseableByteBufferHandler() {
					@Override
					public void failed(IOException e) {
						write.failed(e);
					}
					
					@Override
					public void close() {
						LOGGER.debug("Upload closed");
						write.handle(null, ByteBuffer.wrap(new byte[] { 0 }));
					}
					
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						write.handle(null, buffer);
					}
				});
			}
		});
	}
	*/
}
