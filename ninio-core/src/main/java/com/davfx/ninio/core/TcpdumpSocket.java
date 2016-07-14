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
	
	public static interface Builder extends NinioBuilder<TcpdumpSocket> {
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
				return new TcpdumpSocket(interfaceId, rule, bindAddress);
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
	
	private final String interfaceId;
	private final String rule;
	private final Address bindAddress;
	
	private TcpdumpSocket(String interfaceId, String rule, Address bindAddress) { //, final boolean promiscuous) {
		this.interfaceId = interfaceId;
		this.rule = rule;
		this.bindAddress = bindAddress;
	}
	
	@Override
	public Connecting connect(final Callback callback) {
		final DatagramSocket socket;
		try {
			if (bindAddress == null) {
				socket = new DatagramSocket();
			} else {
				InetSocketAddress a = new InetSocketAddress(bindAddress.host, bindAddress.port); // Note this call blocks to resolve host (DNS resolution) //TODO Test with unresolved
				if (a.isUnresolved()) {
					throw new IOException("Unresolved address: " + bindAddress);
				}
				socket = new DatagramSocket(a);
			}
			try {
				socket.setReceiveBufferSize(READ_BUFFER_SIZE);
				socket.setSendBufferSize(WRITE_BUFFER_SIZE);
				LOGGER.debug("Datagram socket created (bound to {}, port {}, receive buffer size = {}, send buffer size = {})", bindAddress, socket.getLocalPort(), socket.getReceiveBufferSize(), socket.getSendBufferSize());
			} catch (IOException se) {
				socket.close();
				throw se;
			}
		} catch (IOException ae) {
			callback.failed(new IOException("Could not create send socket", ae));
			return new Connecting() {
				@Override
				public void close() {
				}
				@Override
				public void send(Address address, ByteBuffer buffer, Callback callback) {
					callback.failed(new IOException("Failed to be created"));
				}
			};
		}
		
		//
		
		Runnable connected = new Runnable() {
			@Override
			public void run() {
				callback.connected(null);
			}
		};
		
		final TcpdumpReader tcpdumpReader = RAW ? new RawTcpdumpReader(interfaceId.equals("any"), connected) : new HexTcpdumpReader(connected);
		
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
		final Process process;
		try {
			LOGGER.info("In: {}, executing: {}", dir.getCanonicalPath(), Joiner.on(' ').join(toExec));
			process = pb.start();
		} catch (IOException e) {
			socket.close();
			callback.failed(new IOException("Could not create process", e));
			return new Connecting() {
				@Override
				public void close() {
				}
				@Override
				public void send(Address address, ByteBuffer buffer, Callback callback) {
					callback.failed(new IOException("Failed to be created"));
				}
			};
		}
		
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
					code = process.waitFor();
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
				
				process.destroy();
				
				if (code != 0) {
					callback.failed(new IOException("Non zero return code from tcpdump: " + code));
				} else {
					callback.closed();
				}
			}
		});
		
		return new Connecter.Connecting() {
			@Override
			public void send(Address address, ByteBuffer buffer, Callback callback) {
				LOGGER.trace("Sending datagram to: {}", address);
				try {
					DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining(), InetAddress.getByName(address.host), address.port);
					socket.send(packet);
					callback.sent();
				} catch (IOException ioe) {
					callback.failed(ioe);
				}
			}
			
			@Override
			public void close() {
				process.destroy();
				socket.close();
			}
		};
	}
}
