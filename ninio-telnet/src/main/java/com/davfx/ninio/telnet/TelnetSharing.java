package com.davfx.ninio.telnet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.Failable;
import com.davfx.ninio.core.Queue;

public final class TelnetSharing implements AutoCloseable, Closeable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TelnetSharing.class);
	
	private static final class NextCommand {
		public final String command;
		public final String prompt;
		public final TelnetSharingHandler.Callback callback;
		public NextCommand(String prompt, String command, TelnetSharingHandler.Callback callback) {
			this.command = command;
			this.prompt = prompt;
			this.callback = callback;
		}
		@Override
		public String toString() {
			return prompt + command;
		}
	}

	private static final class InitCommand {
		public final String command;
		public final String prompt;
		public final List<TelnetSharingHandler.Callback> callbacks = new LinkedList<>();
		public String response = null;
		public InitCommand(String prompt, String command) {
			this.command = command;
			this.prompt = prompt;
		}
		@Override
		public String toString() {
			return prompt + command;
		}
	}

	private static final Queue DEFAULT_QUEUE = new Queue();

	private final TelnetSharingReadyFactory factory;
	private Queue queue = DEFAULT_QUEUE;
	private final Map<Address, TelnetSharingHandlerManager> map = new HashMap<>();
	
	public TelnetSharing(TelnetSharingReadyFactory factory) {
		this.factory = factory;
	}
	
	@Override
	public void close() {
		queue.post(new Runnable() {
			@Override
			public void run() {
				for (TelnetSharingHandlerManager m : map.values()) {
					m.close();
				}
			}
		});
	}
	
	public TelnetSharing withQueue(Queue queue) {
		this.queue = queue;
		return this;
	}
	
	private static final class TelnetSharingHandlerManager implements Failable, Closeable {
		private static enum State {
			CONNECTING, CONNECTED, WAITING_RESPONSE, DISCONNECTED
		}
		private final Queue queue;
		private final TelnetSharingReadyFactory factory;
		private final Address address;
		private State state = State.DISCONNECTED;
		private CutOnPromptClient.Handler.Write write;
		private final List<InitCommand> initCommands = new ArrayList<>();
		private final Deque<NextCommand> commands = new LinkedList<>();
		private int countOpen = 0;
		
		public TelnetSharingHandlerManager(Queue queue, Address address, TelnetSharingReadyFactory factory) {
			this.queue = queue;
			this.factory = factory;
			this.address = address;
		}
		
		@Override
		public void failed(IOException e) {
			LOGGER.debug("Disconnected with error: {}", e.getMessage());
			for (InitCommand i : initCommands) {
				for (TelnetSharingHandler.Callback callback : i.callbacks) {
					callback.failed(e);
				}
			}
			for (NextCommand c : commands) {
				c.callback.failed(e);
			}
			initCommands.clear();
			commands.clear();
		}
		
		@Override
		public void close() {
			initCommands.clear();
			commands.clear();
			countOpen = 0;
			state = State.DISCONNECTED;
			write = null;
			LOGGER.debug("Disconnected from: {}", address);
		}
		
		public TelnetSharingHandler createHandler() {
			return new TelnetSharingHandler() {
				private boolean open = false;
				private boolean closed = false;
				private int initIndex = 0;

				@Override
				public void init(String prompt, String command, Callback callback) {
					if (state == State.DISCONNECTED) {
						LOGGER.debug("Init command added: {}", command);
						InitCommand i = new InitCommand(prompt, command);
						i.callbacks.add(callback);
						initCommands.add(i);
					} else {
						if (initIndex >= initCommands.size()) {
							LOGGER.warn("Already connecting/connected, could not add init command: {}", command);
							return;
						}
						InitCommand i = initCommands.get(initIndex);
						if (!Objects.equals(i.command, command)) {
							LOGGER.warn("Already connecting/connected, init command differs: {} (previous was: {})", command, i.command);
							return;
						}
						if (i.response != null) {
							callback.handle(i.response);
						} else {
							i.callbacks.add(callback);
						}
					}
					initIndex++;
				}
				
				@Override
				public void write(String prompt, String command, Callback callback) {
					if (!open) {
						open = true;
						countOpen++;
						if (state == State.DISCONNECTED) {
							LOGGER.debug("Connecting to: {}", address);
							state = State.CONNECTING;
							if (initCommands.isEmpty()) {
								throw new IllegalStateException("Init commands required");
							}
							if (initCommands.get(0).command != null) {
								throw new IllegalStateException("The first init command must be null");
							}

							new CutOnPromptClient(factory.create(queue, address), initCommands.get(0).prompt, new CutOnPromptClient.Handler() {
								private int writeIndex = 0;
								
								@Override
								public void failed(IOException e) {
									closed = true;
									TelnetSharingHandlerManager.this.failed(e);
									TelnetSharingHandlerManager.this.close();
								}
								@Override
								public void close() {
									closed = true;
									TelnetSharingHandlerManager.this.failed(new IOException("Closed"));
									TelnetSharingHandlerManager.this.close();
								}
								@Override
								public void connected(Write write) {
									TelnetSharingHandlerManager.this.write = write;
									state = State.CONNECTED;
								}
								@Override
								public void handle(String result) {
									state = State.CONNECTED;
									
									if (writeIndex < initCommands.size()) {
										InitCommand i = initCommands.get(writeIndex);
										i.response = result;
										for (TelnetSharingHandler.Callback callback : i.callbacks) {
											callback.handle(result);
										}
										i.callbacks.clear();
										
										if (writeIndex < (initCommands.size() - 1)) {
											InitCommand n = initCommands.get(writeIndex + 1);
											state = State.WAITING_RESPONSE;
											LOGGER.debug("Prompt: {}", n.prompt);
											LOGGER.debug("Init command sent: {}", n.command);
											write.changePrompt(n.prompt);
											write.write(n.command);
										} else if (!commands.isEmpty()) {
											NextCommand n = commands.getFirst();
											state = State.WAITING_RESPONSE;
											LOGGER.debug("Prompt: {}", n.prompt);
											LOGGER.debug("Command sent: {}", n.command);
											write.changePrompt(n.prompt);
											write.write(n.command);
										}
									} else {
										NextCommand p = commands.removeFirst();
										p.callback.handle(result);
		
										if (!commands.isEmpty()) {
											NextCommand n = commands.getFirst();
											state = State.WAITING_RESPONSE;
											LOGGER.debug("Prompt: {}", n.prompt);
											LOGGER.debug("Command sent: {}", n.command);
											write.changePrompt(n.prompt);
											write.write(n.command);
										}
									}
									
									writeIndex++;
								}
							});
						}
					}
					
					commands.add(new NextCommand(prompt, command, callback));
					if ((state == State.CONNECTED) && commands.isEmpty()) {
						LOGGER.debug("Stalled connection: {}", address);
						LOGGER.debug("Prompt: {}", prompt);
						LOGGER.debug("Command sent: {}", command);
						write.changePrompt(prompt);
						write.write(command);
					}
				}
				
				@Override
				public void close() {
					if (closed) {
						LOGGER.debug("Already closed");
						return;
					}
					
					if (open) {
						countOpen--;
					}
					if (countOpen == 0) {
						LOGGER.debug("Closing");
						write.close();
						TelnetSharingHandlerManager.this.close();
					} else {
						LOGGER.debug("Close requested but there are other clients still connected");
					}
				}
			};
		}
	}
	
	public TelnetSharingHandler client(Address address) {
		TelnetSharingHandlerManager m = map.get(address);
		if (m == null) {
			m = new TelnetSharingHandlerManager(queue, address, factory);
			map.put(address, m);
		}
		
		final TelnetSharingHandler handler = m.createHandler();
		
		return new TelnetSharingHandler() {
			@Override
			public void init(final String prompt, final String command, final Callback callback) {
				queue.post(new Runnable() {
					@Override
					public void run() {
						handler.init(prompt, command, callback);
					}
				});
			}

			@Override
			public void write(final String prompt, final String command, final Callback callback) {
				queue.post(new Runnable() {
					@Override
					public void run() {
						handler.write(prompt, command, callback);
					}
				});
			}

			@Override
			public void close() {
				queue.post(new Runnable() {
					@Override
					public void run() {
						handler.close();
					}
				});
			}
		};
	}
}
