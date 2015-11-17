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
			void setPrompt(String prompt);
			void write(String command);
		}
		void connected(Write write);
		void handle(String result);
	}
	
	public CutOnPromptClient(TelnetReady client, final int limit, final Handler handler) {
		client.connect(new ReadyConnection() {
			private CuttingByteBufferHandler cuttingHandler;
			
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
				cuttingHandler.handle(address, buffer);
			}
			
			@Override
			public void connected(final FailableCloseableByteBufferHandler write) {
				cuttingHandler = new CuttingByteBufferHandler(limit, new FailableCloseableByteBufferHandler() {
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
				});
				
				handler.connected(new Handler.Write() {
					@Override
					public void setPrompt(String prompt) {
						cuttingHandler.setPrompt(ByteBuffer.wrap(prompt.getBytes(Charsets.UTF_8)));
					}
					@Override
					public void write(String command) {
						write.handle(null, ByteBuffer.wrap((command + TelnetSpecification.EOL).getBytes(Charsets.UTF_8)));
					}
					@Override
					public void close() {
						write.close();
					}
				});
			}
		});
	}
}
