package com.davfx.ninio.telnet.v3.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Closing;
import com.davfx.ninio.core.v3.Connecting;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.Ninio;
import com.davfx.ninio.core.v3.NinioBuilder;
import com.davfx.ninio.core.v3.Queue;
import com.davfx.ninio.core.v3.Receiver;
import com.davfx.ninio.core.v3.TcpSocket;
import com.davfx.ninio.telnet.v3.TelnetClient;
import com.davfx.ninio.telnet.v3.TelnetSpecification;
import com.google.common.base.Charsets;

public final class CutOnPromptClient {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CutOnPromptClient.class);
	
	public static void main(String[] args) throws Exception {
		try (Ninio ninio = Ninio.create()) {
			Disconnectable c = ninio.create(CutOnPromptClient.builder().with(Executors.newSingleThreadExecutor()).with(new Handler() {
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
				Thread.sleep(100000);
			} finally {
				c.close();
			}
		}
	}
	
	private static final Charset DEFAULT_CHARSET = Charsets.UTF_8;
	private static final int DEFAULT_LIMIT = 100;
	private static final String DEFAULT_EOL = "\n";
	
	public static interface Handler {
		interface Receive {
			void received(Write write, String result);
		}
		interface Write extends Disconnectable {
			Write write(String command, String prompt, Receive callback);
		}
		void connected(Write write);
		void disconnected();
	}
	
	public static interface Builder extends NinioBuilder<Disconnectable> {
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
			public Connector create(Queue queue) {
				if (builder == null) {
					throw new NullPointerException("builder");
				}
				if (executor == null) {
					throw new NullPointerException("executor");
				}

				final String endOfLine = eol;
				final Charset c = charset;
				
				final Executor e = executor;
				final Handler h = handler;
				
				final InnerHolder innerHolder = new InnerHolder();

				final Handler.Write write = new Handler.Write() {
					@Override
					public void close() {
						innerHolder.connector.close();
					}
					
					@Override
					public Handler.Write write(final String command, final String prompt, final Handler.Receive callback) {
						e.execute(new Runnable() {
							@Override
							public void run() {
								LOGGER.trace("Sending command: {}, with prompt: {}", command, prompt);
								innerHolder.cuttingReceiver.on(ByteBuffer.wrap(prompt.getBytes(c)));
								innerHolder.receiveCallback = callback;
								if (command != null) {
									innerHolder.connector.send(null, ByteBuffer.wrap((command + endOfLine).getBytes(c)));
								}
							}
						});
						return this;
					}
				};
				
				innerHolder.cuttingReceiver = new CuttingReceiver(limit, new Receiver() {
					private InMemoryBuffers buffers = null;
					@Override
					public void received(Connector connector, Address address, final ByteBuffer buffer) {
						e.execute(new Runnable() {
							@Override
							public void run() {
								if (innerHolder.receiveCallback != null) {
									if (buffer == null) {
										innerHolder.receiveCallback.received(write, buffers.toString(c));
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
				});

				innerHolder.connector = builder.connecting(new Connecting() {
					@Override
					public void connected(Address to, final Connector connector) {
						if (h != null) {
							h.connected(write);
						}
					}
				}).receiving(innerHolder.cuttingReceiver).closing(new Closing() {
					@Override
					public void closed() {
						if (h != null) {
							h.disconnected();
						}
					}
				}).failing(new Failing() {
					@Override
					public void failed(IOException e) {
						if (h != null) {
							h.disconnected();
						}
					}
				}).create(queue);
				
				return innerHolder.connector;
			}
		};
	}
	
	private static final class InnerHolder {
		public Handler.Receive receiveCallback;
		public Connector connector;
		public CuttingReceiver cuttingReceiver;
	}
}
