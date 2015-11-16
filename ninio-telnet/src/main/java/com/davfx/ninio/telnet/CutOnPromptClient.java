package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.Failable;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.ReadyConnection;
import com.google.common.base.Charsets;

public final class CutOnPromptClient {
	
	public static interface Handler extends Failable, Closeable {
		interface Write extends Closeable {
			void changePrompt(String prompt);
			void write(String command);
		}
		void connected(Write write);
		void handle(String result);
	}
	
	private final CutOnPromptReadyConnection connection;
	
	public CutOnPromptClient(TelnetReady client, String initialPrompt, final Handler handler) {
		connection = new CutOnPromptReadyConnection(new ReadyConnection() {
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
				handler.handle(new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8));
			}
			
			@Override
			public void connected(final FailableCloseableByteBufferHandler write) {
				handler.connected(new Handler.Write() {
					@Override
					public void changePrompt(String prompt) {
						connection.setPrompt(ByteBuffer.wrap(prompt.getBytes(Charsets.UTF_8)));
					}
					@Override
					public void write(String command) {
						write.handle(null, ByteBuffer.wrap((command + TelnetClient.EOL).getBytes(Charsets.UTF_8)));
					}
					@Override
					public void close() {
						write.close();
					}
				});
			}
		});
		connection.setPrompt(ByteBuffer.wrap(initialPrompt.getBytes(Charsets.UTF_8)));
		client.connect(connection);
	}
}
