package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferAllocator;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.Failable;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.ReadyConnection;
import com.google.common.base.Charsets;

public final class CutOnPromptClient {
	
	public static final class NextCommand {
		public final String command;
		public final String prompt;
		public NextCommand(String prompt, String command) {
			this.command = command;
			this.prompt = prompt;
		}
		public NextCommand(String command) {
			this.command = command;
			prompt = null;
		}
		@Override
		public String toString() {
			return prompt + command;
		}
		
	}
	
	public static interface Handler extends Failable, Closeable {
		NextCommand handle(String result);
	}
	
	private FailableCloseableByteBufferHandler write;
	private final List<ByteBuffer> buffers = new LinkedList<>();
	private String prompt = null;
	private String command = null;
	
	public CutOnPromptClient(TelnetReady client, final Handler handler) {
		client.connect(new CutOnPromptReadyConnection(new ByteBufferAllocator() {
			@Override
			public ByteBuffer allocate() {
				NextCommand next;

				if (!buffers.isEmpty()) {
					int l = 0;
					for (ByteBuffer b : buffers) {
						l += b.remaining();
					}
					byte[] buf = new byte[l];
					int off = 0;
					for (ByteBuffer b : buffers) {
						int len = b.remaining();
						b.get(buf, off, len);
						off += len;
					}
					
					String s = new String(buf, Charsets.UTF_8);
					
					next = handler.handle(s);
					
					buffers.clear();
				} else {
					next = handler.handle(null);
				}

				if (command != null) {
					write.handle(null, ByteBuffer.wrap((command + TelnetClient.EOL).getBytes(Charsets.UTF_8)));
					command = null;
				}
				
				if (next != null) {
					command = next.command;
					if (next.prompt != null) {
						prompt = next.prompt;
					}
				}
				
				return ByteBuffer.wrap(prompt.getBytes(Charsets.UTF_8));
			}
		}, new ReadyConnection() {
			@Override
			public void failed(IOException e) {
				handler.failed(e);
			}
			
			@Override
			public void close() {
				handler.close();
			}
			
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				buffers.add(buffer);
			}
			
			@Override
			public void connected(FailableCloseableByteBufferHandler write) {
				CutOnPromptClient.this.write = write;
			}
		}));
	}
}
