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
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.core.SocketReadyFactory;
import com.davfx.ninio.util.QueueScheduled;
import com.davfx.util.ConfigUtils;
import com.davfx.util.DateUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class TelnetSharing implements AutoCloseable, Closeable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TelnetSharing.class);

	private static final Config CONFIG = ConfigFactory.load(TelnetSharing.class.getClassLoader());

	private static final double CONNECTIONS_TIME_TO_LIVE = ConfigUtils.getDuration(CONFIG, "ninio.telnet.sharing.ttl");
	private static final double CONNECTIONS_CHECK_TIME = ConfigUtils.getDuration(CONFIG, "ninio.telnet.sharing.check");
	private static final int BUFFERING_LIMIT = CONFIG.getBytes("ninio.telnet.sharing.limit").intValue();

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

	private Queue queue = DEFAULT_QUEUE;
	private final Map<Address, TelnetSharingHandlerManager> map = new HashMap<>();
	private ReadyFactory readyFactory = null;
	
	public TelnetSharing() {
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
	
	public TelnetSharing override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
	
	private static final class TelnetSharingHandlerManager {
		private static enum State {
			CONNECTING, CONNECTED, WAITING_RESPONSE, DISCONNECTED
		}
		private final Queue queue;
		private final ReadyFactory readyFactory;
		private final Address address;
		private State state = State.DISCONNECTED;
		private CutOnPromptClient.Handler.Write write;
		private final List<InitCommand> initCommands = new ArrayList<>();
		private final Deque<NextCommand> commands = new LinkedList<>();
		private double closeDate = 0d;
		private final Closeable closeable;
		private boolean closed = false;
		
		public TelnetSharingHandlerManager(Queue queue, ReadyFactory readyFactory, Address address) {
			this.queue = queue;
			this.readyFactory = readyFactory;
			this.address = address;
			
			closeable = QueueScheduled.schedule(queue, CONNECTIONS_CHECK_TIME, new Runnable() {
				@Override
				public void run() {
					double now = DateUtils.now();
					if ((state == State.CONNECTED) && (closeDate > 0d) && (closeDate <= now)) {
						doClose(null);
					}
				}
			});
		}
		
		void close() {
			closed = true;
			LOGGER.trace("Closed: {}", address);
			closeable.close();
			if (write != null) {
				write.close();
			}
			doClose(null);
		}
		
		private void doClose(IOException e) {
			if (e != null) {
				LOGGER.debug("Disconnected with error: {}", e.getMessage());
				for (InitCommand i : initCommands) {
					for (TelnetSharingHandler.Callback callback : i.callbacks) {
						callback.failed(e);
					}
				}
				for (NextCommand c : commands) {
					c.callback.failed(e);
				}
			}

			initCommands.clear();
			commands.clear();

			state = State.DISCONNECTED;
			write = null;
			LOGGER.debug("Disconnected from: {}", address);
		}
		
		public TelnetSharingHandler createHandler(final TelnetSharingReadyFactory factory) {
			return new TelnetSharingHandler() {
				private int initIndex = 0;

				@Override
				public void init(String command, String prompt, Callback callback) {
					if (state == State.DISCONNECTED) {
						LOGGER.trace("Init command added: {}", command);
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
				public void write(String command, String prompt, Callback callback) {
					if (state == State.DISCONNECTED) {
						LOGGER.trace("Connecting to: {}", address);
						state = State.CONNECTING;
						if (initCommands.isEmpty()) {
							throw new IllegalStateException("Init commands required");
						}

						new CutOnPromptClient(factory.create(queue, (readyFactory == null) ? new SocketReadyFactory(queue) : readyFactory, address), BUFFERING_LIMIT, new CutOnPromptClient.Handler() {
							private int writeIndex = 0;
							
							@Override
							public void failed(IOException e) {
								doClose(e);
							}
							@Override
							public void close() {
								doClose(new IOException("Closed"));
							}
							@Override
							public void connected(Write write) {
								if (closed) {
									write.close();
									return;
								}
								TelnetSharingHandlerManager.this.write = write;
								state = State.CONNECTED;
								LOGGER.trace("State: {}", state);

								InitCommand n = initCommands.get(0);
								LOGGER.trace("Prompt: {}", n.prompt);
								LOGGER.trace("Init command sent: {}", n.command);
								doWrite(n.prompt, n.command);
							}
							@Override
							public void handle(String result) {
								LOGGER.trace("Received: {}", result);
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
										LOGGER.trace("Prompt: {}", n.prompt);
										LOGGER.trace("Init command sent: {}", n.command);
										doWrite(n.prompt, n.command);
									} else if (!commands.isEmpty()) {
										NextCommand n = commands.getFirst();
										LOGGER.trace("Prompt: {}", n.prompt);
										LOGGER.trace("Command sent: {}", n.command);
										doWrite(n.prompt, n.command);
									}
								} else {
									NextCommand p = commands.removeFirst();
									p.callback.handle(result);
	
									if (!commands.isEmpty()) {
										NextCommand n = commands.getFirst();
										LOGGER.trace("Prompt: {}", n.prompt);
										LOGGER.trace("Command sent: {}", n.command);
										doWrite(n.prompt, n.command);
									}
								}
								
								writeIndex++;
							}
						});
					}

					LOGGER.trace("State: {}", state);

					if ((state == State.CONNECTED) && commands.isEmpty()) {
						commands.add(new NextCommand(prompt, command, callback));
						LOGGER.trace("Stalled connection: {}", address);
						LOGGER.trace("Prompt: {}", prompt);
						LOGGER.trace("Command sent: {}", command);
						doWrite(prompt, command);
					} else {
						commands.add(new NextCommand(prompt, command, callback));
					}
				}
				
				private void doWrite(String prompt, String command) {
					state = State.WAITING_RESPONSE;
					closeDate = DateUtils.now() + CONNECTIONS_TIME_TO_LIVE;
					write.setPrompt(prompt);
					if (command != null) {
						String s = command + factory.eol();
						LOGGER.trace("Sent: /{}/", s);
						write.write(s);
					}
				}
			};
		}
	}
	
	public TelnetSharingHandler client(TelnetSharingReadyFactory factory, Address address) {
		TelnetSharingHandlerManager m = map.get(address);
		if (m == null) {
			m = new TelnetSharingHandlerManager(queue, readyFactory, address);
			map.put(address, m);
		}
		
		final TelnetSharingHandler handler = m.createHandler(factory);
		
		return new TelnetSharingHandler() {
			@Override
			public void init(final String command, final String prompt, final Callback callback) {
				queue.post(new Runnable() {
					@Override
					public void run() {
						handler.init(command, prompt, callback);
					}
				});
			}

			@Override
			public void write(final String command, final String prompt, final Callback callback) {
				queue.post(new Runnable() {
					@Override
					public void run() {
						handler.write(command, prompt, callback);
					}
				});
			}
		};
	}
}
