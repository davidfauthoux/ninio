package com.davfx.ninio.trash;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.regex.Pattern;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.http.Http;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpServer;
import com.davfx.ninio.http.HttpServerConfigurator;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;
import com.davfx.ninio.http.util.JsonDirectoryHttpServerHandler;
import com.davfx.ninio.http.util.PatternDispatchHttpServerHandler;
import com.davfx.ninio.http.websocket.WebsocketHttpServerHandler;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;

//Sec-WebSocket-Key=Jc0pxsIaE4PCWb0zhx4Hhw==
//Cache-Control=no-cache
//Connection=Upgrade
//Sec-WebSocket-Version=13
//Upgrade=websocket
//Sec-WebSocket-Extensions=permessage-deflate; client_max_window_bits, x-webkit-deflate-frame

/*
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: HSmrc0sMlYUkAGmm5OPpG2HaGWk=
Sec-WebSocket-Protocol: chat
 */
public class TestWsServer {
	public static void main(String[] args) throws Exception {
		try (Queue queue = new Queue()) {
			new HttpServer(new HttpServerConfigurator(queue).withAddress(new Address(8080)), new HttpServerHandlerFactory() {
				@Override
				public void failed(IOException e) {
				}
				@Override
				public void closed() {
				}
				
				@Override
				public HttpServerHandler create() {
					return new PatternDispatchHttpServerHandler()
					.add(Pattern.compile(".*\\.html"), new JsonDirectoryHttpServerHandler(new File(".")))
					.add(Pattern.compile(".*"), new WebsocketHttpServerHandler(new ReadyConnection() {
						@Override
						public void failed(IOException e) {
							System.out.println("### FAILED");
						}
						@Override
						public void close() {
							System.out.println("### CLOSE");
						}
						
						CloseableByteBufferHandler write;
						@Override
						public void handle(Address address, ByteBuffer buffer) {
							byte[] b = new byte[buffer.remaining()];
							buffer.get(b);
							System.out.println("### HANDLE " + new String(b, Charsets.UTF_8));
							write.handle(null, ByteBuffer.wrap(b));
						}
						
						@Override
						public void connected(FailableCloseableByteBufferHandler write) {
							System.out.println("### CONNECTED");
							this.write = write;
							
						}
					}).withTextResponses(true))
					.add(Pattern.compile(".*"), new HttpServerHandler() {
								HttpRequest request;
								@Override
								public void ready(final Write write) {
									System.out.println("# READY");
									String wsKey = request.getHeaders().get("Sec-WebSocket-Key");
									String wsVersion = request.getHeaders().get("Sec-WebSocket-Version");
									System.out.println("VERSION " + wsVersion);
									HttpResponse response = new HttpResponse(101, "Switching Protocols");
									response.getHeaders().put("Connection", "Upgrade");
									response.getHeaders().put("Upgrade", "websocket");
									response.getHeaders().put("Sec-WebSocket-Accept", BaseEncoding.base64().encode(Hashing.sha1().hashBytes((wsKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(Charsets.UTF_8)).asBytes()));
									response.getHeaders().put(Http.TRANSFER_ENCODING, "none");
									
									write.write(response);

									new Thread(new Runnable() {
										@Override
										public void run() {
											while (true) {
												try {
													Thread.sleep(2000);
												} catch (InterruptedException e) {
												}
												byte[] bbb = new byte[100];
												ByteBuffer bb = ByteBuffer.wrap(bbb);
												bb.put((byte) 0x81); //TEXT
												byte[] hello = "AAA".getBytes(Charsets.UTF_8);
												bb.put((byte) hello.length);
												bb.put(hello);
												bb.flip();
												System.out
														.println("WRITING " + bb.remaining());
												write.handle(null, bb);
												//write.handle(null, ByteBuffer.wrap(new byte[777]));
											}
										}
									}).start();
								}
								
								@Override
								public void handle(Address address, ByteBuffer buffer) {
									System.out.println("# HANDLE " + buffer.remaining());
									while (buffer.hasRemaining()) {
										System.out.println(Integer.toHexString(buffer.get() & 0xFF));
									}
								}
								
								@Override
								public void handle(HttpRequest request) {
									System.out.println("# HANDLE REQUEST " + request.getPath());
									this.request = request;
								}
								
								@Override
								public void failed(IOException e) {
									System.out.println("# FAILED");
								}
								
								@Override
								public void close() {
									System.out.println("# CLOSED");
								}
					});
				}
			});
					/*
			new HttpServer(queue, new Address(8080), new HttpServerHandlerFactory() {
				public void closed() {
					System.out.println("CLOSED");
				}
				@Override
				public void failed(IOException e) {
					System.out.println("FAILED");
				}
				@Override
				public HttpServerHandler create() {
					return new HttpServerHandler() {
						Write write;
						@Override
						public void ready(Write write) {
							System.out.println("# READY");
							this.write = write;
							HttpResponse response = new HttpResponse(Http.Status.OK, Http.Message.OK);
							write.write(response);
							new Thread(new Runnable() {
								@Override
								public void run() {
									while (true) {
										try {
											Thread.sleep(20000);
										} catch (InterruptedException e) {
										}
										write.handle(null, ByteBuffer.wrap(new byte[777]));
									}
								}
							}).start();
						}
						
						@Override
						public void handle(Address address, ByteBuffer buffer) {
							System.out.println("# HANDLE " + buffer.remaining());
						}
						
						@Override
						public void handle(HttpRequest request) {
							System.out.println("# HANDLE REQUEST " + request.getPath());
						}
						
						@Override
						public void failed(IOException e) {
							System.out.println("# FAILED");
						}
						
						@Override
						public void close() {
							System.out.println("# CLOSED");
						}
					};
				}
			});
			Thread.sleep(1000);
			new HttpClient(queue, null).send(new HttpRequest(new Address("localhost", 8080), false, HttpRequest.Method.GET, "/a/b/c"), new HttpClientHandler() {
				ByteBufferHandler write;
				@Override
				public void handle(Address address, ByteBuffer buffer) {
					System.out.println("## RECEIVED " + buffer.remaining());
				}
				@Override
				public void received(HttpResponse response) {
					System.out.println("## RECEIVED RESPONSE " + response.getStatus());
				}
				@Override
				public void ready(ByteBufferHandler write) {
					System.out.println("## READY");
					this.write = write;
					new Thread(new Runnable() {
						@Override
						public void run() {
							while (true) {
								try {
									Thread.sleep(2000);
								} catch (InterruptedException e) {
								}
								write.handle(null, ByteBuffer.wrap(new byte[666]));
							}
						}
					}).start();
				}
				@Override
				public void close() {
					System.out.println("## FINISHED");
				}
				@Override
				public void failed(IOException e) {
					System.out.println("## FAILED");
				};
			});*/
			Thread.sleep(100000);
		}
	}
}
