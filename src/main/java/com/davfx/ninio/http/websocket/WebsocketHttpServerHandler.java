package com.davfx.ninio.http.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpServerHandler;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;

public final class WebsocketHttpServerHandler implements HttpServerHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketHttpServerHandler.class);

	private final ReadyConnection wrappee;
	private HttpRequest request;
	private HttpServerHandler.Write write;
	private boolean textResponses = false;
	
	public WebsocketHttpServerHandler(ReadyConnection wrappee) {
		this.wrappee = wrappee;
	}
	
	public WebsocketHttpServerHandler withTextResponses(boolean textResponses) {
		this.textResponses = textResponses;
		return this;
	}
	
	@Override
	public void ready(final Write write) {
		this.write = write;

		String wsKey = request.getHeaders().get("Sec-WebSocket-Key");
		String wsVersion = request.getHeaders().get("Sec-WebSocket-Version");

		if (!"13".equals(wsVersion)) {
			write.failed(new IOException("Current implementation does not handle this version: " + wsVersion));
			return;
		}

		HttpResponse response = new HttpResponse(101, "Switching Protocols");
		response.getHeaders().put("Connection", "Upgrade");
		response.getHeaders().put("Upgrade", "websocket");
		response.getHeaders().put("Sec-WebSocket-Accept", BaseEncoding.base64().encode(Hashing.sha1().hashBytes((wsKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(Charsets.UTF_8)).asBytes()));
		// We don't need this hack anymore // response.getHeaders().put(Http.TRANSFER_ENCODING, "none");
		
		write.write(response);

		LOGGER.debug("Client connected");
		
		wrappee.connected(new FailableCloseableByteBufferHandler() {
			@Override
			public void close() {
				write.handle(null, WebsocketFrameReader.headerOf(true, 0x08, null));
			}
			@Override
			public void failed(IOException e) {
				write.failed(e);
			}
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				write.handle(null, WebsocketFrameReader.headerOf(true, textResponses ? 0x01 : 0x02, buffer));
				write.handle(null, buffer);
			}
		});
	}

	private static final int PING_MAX_BUFFER_SIZE = 1024;
	private WebsocketFrameReader websocketFrameReader = null;
	
	@Override
	public void handle(Address address, ByteBuffer buffer) {
		websocketFrameReader.handle(address, buffer);
	}

	@Override
	public void handle(HttpRequest request) {
		this.request = request;
		websocketFrameReader = new WebsocketFrameReader(new WebsocketFrameReader.Handler() {
			private ByteBuffer pingBuffer = null;
			@Override
			public void failed(IOException e) {
				write.failed(e);
			}
			@Override
			public void handle(int opcode, long frameLength, ByteBuffer partialBuffer) {
				if (opcode == 0x09) {
					if (pingBuffer == null) {
						if (frameLength > PING_MAX_BUFFER_SIZE) {
							write.failed(new IOException("Ping frame is too big: " + frameLength));
							return;
						}
						pingBuffer = ByteBuffer.allocate((int) frameLength);
					}
					
					pingBuffer.put(partialBuffer);
					
					if (pingBuffer.position() == frameLength) {
						pingBuffer.flip();
						write.handle(null, WebsocketFrameReader.headerOf(true, 0x0A, pingBuffer));
						write.handle(null, pingBuffer);
						pingBuffer = null;
					}
					
					return;
				}
				
				if ((opcode == 0x01) || (opcode == 0x02)) {
					wrappee.handle(null, partialBuffer);
					return;
				}

				if (opcode == 0x08) {
					LOGGER.debug("Connection closed");
					write.close();
					wrappee.close();
					return;
				}
				
				write.failed(new IOException("Current implementation does not handle this opcode: " + opcode));
			}
		});
	}
	
	@Override
	public void close() {
		wrappee.close();
	}
	
	@Override
	public void failed(IOException e) {
		wrappee.failed(e);
	}
}
