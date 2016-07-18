package com.davfx.ninio.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.ClassThreadFactory;
import com.davfx.ninio.util.ConfigUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

public final class TcpdumpSocket implements Connecter {
	
	public static interface Builder extends NinioBuilder<Connecter> {
		Builder on(String interfaceId);
		Builder rule(String rule);
		Builder bind(Address bindAddress);
	}

	private static final Config CONFIG = ConfigUtils.load(TcpdumpSocket.class);

	private static final String TCPDUMP_DEFAULT_INTERFACE_ID = CONFIG.getString("tcpdump.interface");
	private static final String TCPDUMP_DEFAULT_RULE = CONFIG.getString("tcpdump.rule");

	public static Builder builder() {
		return new Builder() {
			private String interfaceId = TCPDUMP_DEFAULT_INTERFACE_ID;
			private String rule = TCPDUMP_DEFAULT_RULE;

			private Address bindAddress = null;

			@Override
			public Builder on(String interfaceId) {
				this.interfaceId = interfaceId;
				return this;
			}
			@Override
			public Builder rule(String rule) {
				this.rule = rule;
				return this;
			}
			
			@Override
			public Builder bind(Address bindAddress) {
				this.bindAddress = bindAddress;
				return this;
			}
			
			@Override
			public TcpdumpSocket create(Queue queue) {
				if (interfaceId == null) {
					throw new NullPointerException("interfaceId");
				}
				return new TcpdumpSocket(queue, interfaceId, rule, bindAddress);
			}
		};
	}
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TcpdumpSocket.class);
	
	private static final boolean RAW;
	static {
		String mode = CONFIG.getString("tcpdump.mode");
		LOGGER.debug("Tcpdump mode = {}", mode);
		if (mode.equals("raw")) {
			RAW = true;
		} else if (mode.equals("hex")) {
			RAW = false;
		} else {
			throw new ConfigException.BadValue("tcpdump.mode", "Invalid: " + mode + ", only 'raw' and 'hex' allowed");
		}
	}
	private static final String TCPDUMP_COMMAND = CONFIG.getString("tcpdump.path");
	private static final int READ_BUFFER_SIZE = CONFIG.getBytes("tcpdump.datagram.read.size").intValue();
	private static final int WRITE_BUFFER_SIZE = CONFIG.getBytes("tcpdump.datagram.write.size").intValue();


	private static void execute(String name, Runnable runnable) {
		new ClassThreadFactory(TcpdumpSocket.class, name).newThread(runnable).start();
	}
	
	private final Queue queue;
	
	private final String interfaceId;
	private final String rule;
	private final Address bindAddress;

	private final Object lock = new Object();
	private DatagramSocket socket = null;
	private Process process = null;
	private boolean closed = false;

	private static final class ToWrite {
		public final Address address;
		public final ByteBuffer buffer;
		public final SendCallback callback;
		public ToWrite(Address address, ByteBuffer buffer, SendCallback callback) {
			this.address = address;
			this.buffer = buffer;
			this.callback = callback;
		}
	}

	private final Deque<ToWrite> toWriteQueue = new LinkedList<>();

	private TcpdumpSocket(Queue queue, String interfaceId, String rule, Address bindAddress) { //, final boolean promiscuous) {
		this.queue = queue;
		this.interfaceId = interfaceId;
		this.rule = rule;
		this.bindAddress = bindAddress;
	}
	
	@Override
	public void connect(final Connection callback) {
		final DatagramSocket s;
		try {
			if (bindAddress == null) {
				s = new DatagramSocket();
			} else {
				InetSocketAddress a = new InetSocketAddress(bindAddress.host, bindAddress.port); // Note this call blocks to resolve host (DNS resolution) //TODO Test with unresolved
				if (a.isUnresolved()) {
					throw new IOException("Unresolved address: " + bindAddress);
				}
				s = new DatagramSocket(a);
			}
			try {
				s.setReceiveBufferSize(READ_BUFFER_SIZE);
				s.setSendBufferSize(WRITE_BUFFER_SIZE);
				LOGGER.debug("Datagram socket created (bound to {}, port {}, receive buffer size = {}, send buffer size = {})", bindAddress, s.getLocalPort(), s.getReceiveBufferSize(), s.getSendBufferSize());
			} catch (IOException se) {
				s.close();
				throw se;
			}
		} catch (final IOException ee) {
			synchronized (lock) {
				closed = true;
				queue.execute(new Runnable() {
					@Override
					public void run() {
						callback.failed(new IOException("Could not create send socket", ee));
					}
				});
				return;
			}
		}
		
		//
		
		final TcpdumpReader tcpdumpReader = RAW ? new RawTcpdumpReader(interfaceId.equals("any")) : new HexTcpdumpReader();
		
		File dir = new File(".");

		List<String> toExec = new LinkedList<String>();
		toExec.add(TCPDUMP_COMMAND);
		//%% toExec.add("-w");
		//%% toExec.add("-"); // Try with /dev/stdout
		toExec.add("-i");
		toExec.add(interfaceId);
		toExec.add("-nn");
		for (String o : tcpdumpReader.tcpdumpOptions()) {
			toExec.add(o);
		}
		toExec.add("-K");
		// if (!promiscuous) {
		toExec.add("-p");
		// }
		toExec.add("-q");
		toExec.add("-s");
		toExec.add("0");
		// toExec.add("-U"); // Unbuffers output
		if (rule != null) {
			for (String p : Splitter.on(' ').split(rule)) {
				String r = p.trim();
				if (!r.isEmpty()) {
					toExec.add(r);
				}
			}
		}
		
		ProcessBuilder pb = new ProcessBuilder(toExec);
		pb.directory(dir);
		final Process p;
		try {
			LOGGER.info("In: {}, executing: {}", dir.getCanonicalPath(), Joiner.on(' ').join(toExec));
			p = pb.start();
		} catch (final IOException ee) {
			s.close();
			synchronized (lock) {
				closed = true;
				queue.execute(new Runnable() {
					@Override
					public void run() {
						callback.failed(new IOException("Could not create process", ee));
					}
				});
				return;
			}
		}

		Deque<ToWrite> q;
		synchronized (lock) {
			if (closed) {
				queue.execute(new Runnable() {
					@Override
					public void run() {
						callback.failed(new IOException("Closed"));
					}
				});
				return;
			}
			
			socket = s;
			process = p;

			queue.execute(new Runnable() {
				@Override
				public void run() {
					callback.connected(null);
				}
			});
			
			q = toWriteQueue;
		}
		
		for (final ToWrite toWrite : q) {
			try {
				DatagramPacket packet = new DatagramPacket(toWrite.buffer.array(), toWrite.buffer.arrayOffset() + toWrite.buffer.position(), toWrite.buffer.remaining(), InetAddress.getByName(toWrite.address.host), toWrite.address.port);
				s.send(packet);

				queue.execute(new Runnable() {
					@Override
					public void run() {
						toWrite.callback.sent();
					}
				});
			} catch (final Exception e) {
				queue.execute(new Runnable() {
					@Override
					public void run() {
						toWrite.callback.failed(new IOException("Could not write", e));
					}
				});
			}
		}
		q.clear();
		
		final InputStream error = process.getErrorStream();
		execute("err", new Runnable() {
			@Override
			public void run() {
				try {
					try {
						BufferedReader r = new BufferedReader(new InputStreamReader(error));
						while (true) {
							String line = r.readLine();
							if (line == null) {
								break;
							}
							LOGGER.debug("Tcpdump message: {}", line);
						}
					} finally {
						error.close();
					}
				} catch (IOException e) {
					LOGGER.trace("Error in tcpdump process", e);
				}
			}
		});
		
		final InputStream input = process.getInputStream();
		execute("in", new Runnable() {
			@Override
			public void run() {
				try {
					try {
						tcpdumpReader.read(input, new TcpdumpReader.Handler() {
							@Override
							public void handle(double timestamp, Address sourceAddress, Address destinationAddress, ByteBuffer buffer) {
								callback.received(sourceAddress, buffer);
							}
						});
					} finally {
						input.close();
					}
				} catch (IOException e) {
					LOGGER.trace("Error in tcpdump process", e);
				}
			}
		});

		execute("wait", new Runnable() {
			@Override
			public void run() {
				int code;
				try {
					code = p.waitFor();
				} catch (InterruptedException e) {
					code = -1;
				}

				try {
					error.close();
				} catch (IOException e) {
				}
				try {
					input.close();
				} catch (IOException e) {
				}
				
				p.destroy();
				s.close();
				
				if (code != 0) {
					final int c = code;
					queue.execute(new Runnable() {
						@Override
						public void run() {
							callback.failed(new IOException("Non zero return code from tcpdump: " + c));
						}
					});
				} else {
					queue.execute(new Runnable() {
						@Override
						public void run() {
							callback.closed();
						}
					});
				}
			}
		});
	}
	
	@Override
	public void send(Address address, ByteBuffer buffer, final SendCallback callback) {
		LOGGER.trace("Sending datagram to: {}", address);

		try {
			DatagramSocket s;
			synchronized (lock) {
				if (closed) {
					throw new IOException("Closed");
				}
				if (socket == null) {
					toWriteQueue.addLast(new ToWrite(address, buffer, callback));
					return;
				}
				s = socket;
			}

			DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining(), InetAddress.getByName(address.host), address.port);
			s.send(packet);

			queue.execute(new Runnable() {
				@Override
				public void run() {
					callback.sent();
				}
			});
		} catch (final Exception e) {
			queue.execute(new Runnable() {
				@Override
				public void run() {
					callback.failed(new IOException("Could not write", e));
				}
			});
		}
	}
	
	@Override
	public void close() {
		DatagramSocket s;
		Process p;
		synchronized (lock) {
			if (closed) {
				return;
			}
			closed = true;
			s = socket;
			p = process;
		}
		
		if (s != null) {
			s.close();
		}
		if (p != null) {
			p.destroy();
		}
	}
}
