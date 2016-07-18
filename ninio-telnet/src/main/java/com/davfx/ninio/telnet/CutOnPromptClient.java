package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.InMemoryBuffers;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.TcpSocket;
import com.google.common.base.Charsets;

public final class CutOnPromptClient implements Connecter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CutOnPromptClient.class);
	/*%%
	public static void main(String[] args) throws Exception {
		try (Ninio ninio = Ninio.create()) {
			Disconnectable c = ninio.create(CutOnPromptClient.builder().with(new ThreadingSerialExecutor(CutOnPromptClient.class)).with(new Handler() {
				@Override
				public void disconnected() {
					System.out.println("DISCONNECTED");
				}
				@Override
				public void connected(Write write) {
					System.out.println("CONNECTED");
					write.write(null, "login: ", new Handler.Receive() {
						@Override
						public void received(Write write, String result) {
							System.out.println("/1/" + result + "/");
							write.write("davidfauthoux", "Password:", new Handler.Receive() {
								@Override
								public void received(Write write, String result) {
									System.out.println("/2/" + result + "/");
									write.write("mypassword", "davidfauthoux$ ", new Handler.Receive() {
										@Override
										public void received(Write write, String result) {
											System.out.println("/3/" + result + "/");
											write.write("ls", "davidfauthoux$ ", new Handler.Receive() {
												@Override
												public void received(Write write, String result) {
													System.out.println("/4/" + result + "/");
												}
											});
										}
									});
								}
							});
						}
					});
				}
			}).with(TelnetClient.builder().to(new Address(Address.LOCALHOST, TelnetSpecification.DEFAULT_PORT))));
			try {
				Thread.sleep(60000);
			} finally {
				c.close();
			}
		}
	}*/
	
	private static final Charset DEFAULT_CHARSET = Charsets.UTF_8;
	private static final int DEFAULT_LIMIT = 100;
	private static final String DEFAULT_EOL = TelnetSpecification.EOL;
	
	public static interface Handler {
		interface Receive {
			void received(String result);
		}
		interface Write extends Disconnectable {
			Write write(String command, String prompt, Receive callback);
		}
		void connected(Write write);
	}
	
	public static interface Builder extends NinioBuilder<Connecter> {
		Builder with(TcpSocket.Builder builder);
		Builder with(Handler handler);
		Builder with(Executor executor);
		Builder charset(Charset charset);
		Builder limit(int limit);
		Builder eol(String eol);
	}

	public static Builder builder() {
		return new Builder() {
			private Handler handler = null;
			private TcpSocket.Builder builder = TelnetClient.builder();
			
			private Executor executor = null;
			
			private Charset charset = DEFAULT_CHARSET;
			private int limit = DEFAULT_LIMIT;
			private String eol = DEFAULT_EOL;
			
			@Override
			public Builder with(Handler handler) {
				this.handler = handler;
				return this;
			}
			
			@Override
			public Builder with(Executor executor) {
				this.executor = executor;
				return this;
			}
			
			@Override
			public Builder with(TcpSocket.Builder builder) {
				this.builder = builder;
				return this;
			}
			
			@Override
			public Builder charset(Charset charset) {
				this.charset = charset;
				return this;
			}
			@Override
			public Builder limit(int limit) {
				this.limit = limit;
				return this;
			}
			@Override
			public Builder eol(String eol) {
				this.eol = eol;
				return this;
			}
			
			@Override
			public Connecter create(Queue queue) {
				if (builder == null) {
					throw new NullPointerException("builder");
				}
				if (executor == null) {
					throw new NullPointerException("executor");
				}
				if (handler == null) {
					throw new NullPointerException("handler");
				}

				return new CutOnPromptClient(builder.create(queue), executor, eol, charset, limit, handler);
			}
		};
	}
	
	private Handler.Receive currentReceiveCallback;
	private final Connecter connecter;
	private CuttingReceiver cuttingReceiver;
	private final Executor executor;
	private final Handler handler;
	private final String endOfLine;
	private final Charset charset;
	private final int limit;

	private CutOnPromptClient(Connecter connecter, Executor executor, String endOfLine, Charset charset, int limit, Handler handler) {
		this.connecter = connecter;
		this.executor = executor;
		this.handler = handler;
		this.endOfLine = endOfLine;
		this.charset = charset;
		this.limit = limit;
	}
	
	@Override
	public void connect(final Connecter.ConnectCallback callback) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				cuttingReceiver = new CuttingReceiver(limit, new Connecter.ConnectCallback() {
					private InMemoryBuffers buffers = null;
					@Override
					public void received(Address address, final ByteBuffer buffer) {
						executor.execute(new Runnable() {
							@Override
							public void run() {
								if (currentReceiveCallback != null) {
									if (buffer == null) {
										currentReceiveCallback.received(buffers.toString(charset));
										buffers = null;
									} else {
										if (buffers == null) {
											buffers = new InMemoryBuffers();
										}
										buffers.add(buffer);
									}
								}
							}
						});
					}
					
					@Override
					public void closed() {
						callback.closed();
					}
					@Override
					public void failed(IOException ioe) {
						callback.failed(ioe);
					}
					@Override
					public void connected(Address address) {
						handler.connected(new Handler.Write() {
							@Override
							public void close() {
								executor.execute(new Runnable() {
									@Override
									public void run() {
										connecter.close();
									}
								});
							}
							
							@Override
							public Handler.Write write(final String command, final String prompt, final Handler.Receive receiveCallback) {
								executor.execute(new Runnable() {
									@Override
									public void run() {
										LOGGER.trace("Sending command: {}, with prompt: {}", command, prompt);
										cuttingReceiver.on(ByteBuffer.wrap(prompt.getBytes(charset)));
										currentReceiveCallback = receiveCallback;
										if (command != null) {
											connecter.send(null, ByteBuffer.wrap((command + endOfLine).getBytes(charset)), new Connecter.SendCallback() {
												@Override
												public void sent() {
												}
												@Override
												public void failed(IOException ioe) {
													callback.failed(ioe);
												}
											});
										}
									}
								});
								return this;
							}
						});
						
						callback.connected(address);
					}
				});

				connecter.connect(cuttingReceiver);
			}
		});
	}
	
	public void send(final Address address, final ByteBuffer buffer, final SendCallback callback) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				connecter.send(address, buffer, callback);
			}
		});
	}
	
	@Override
	public void close() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				connecter.close();
			}
		});
	}
}
