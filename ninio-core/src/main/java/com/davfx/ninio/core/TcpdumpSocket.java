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

public final class TcpdumpSocket implements Connector {
	
	public static interface Builder extends NinioBuilder<Connector> {
		Builder on(String interfaceId);
		Builder rule(String rule);
		Builder bind(Address bindAddress);
		Builder failing(Failing failing);
		Builder closing(Closing closing);
		Builder connecting(Connecting connecting);
		Builder receiving(Receiver receiver);
	}

	public static Builder builder() {
		return new Builder() {
			private String interfaceId = null;
			private String rule = null;

			private Address bindAddress = null;

			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			
			@Override
			public Builder closing(Closing closing) {
				this.closing = closing;
				return this;
			}
		
			@Override
			public Builder connecting(Connecting connecting) {
				this.connecting = connecting;
				return this;
			}
			
			@Override
			public Builder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public Builder receiving(Receiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
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
			public Connector create(Queue queue) {
				if (interfaceId == null) {
					throw new NullPointerException("interfaceId");
				}
				return new TcpdumpSocket(interfaceId, rule, bindAddress, connecting, failing, closing, receiver);
			}
		};
	}
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TcpdumpSocket.class);
	
	private static final Config CONFIG = ConfigUtils.load(TcpdumpSocket.class);

	private static final boolean RAW;
	static {
		String mode = CONFIG.getString("tcpdump.mode");
		if (mode.equals("raw")) {
			RAW = true;
		} else if (mode.equals("hex")) {
			RAW = false;
		} else {
			throw new ConfigException.BadValue("ninio.tcpdump.mode", "Invalid: " + mode + ", only 'raw' and 'hex' allowed");
		}
	}
	private static final String TCPDUMP_COMMAND = CONFIG.getString("tcpdump.path");
	private static final int READ_BUFFER_SIZE = CONFIG.getBytes("tcpdump.datagram.read.size").intValue();
	private static final int WRITE_BUFFER_SIZE = CONFIG.getBytes("tcpdump.datagram.write.size").intValue();


	private static void execute(String name, Runnable runnable) {
		new ClassThreadFactory(TcpdumpSocket.class, name).newThread(runnable).start();
	}
	
	private DatagramSocket socket = null;
	private Process process = null;
	
	private TcpdumpSocket(String interfaceId, String rule, Address bindAddress, Connecting connecting, final Failing failing, final Closing closing, final Receiver receiver) { //, final boolean promiscuous) {
		DatagramSocket s;
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
		} catch (IOException ae) {
			if (failing != null) {
				failing.failed(new IOException("Could not create send socket", ae));
			}
			s = null;
		}
		
		if (s == null) {
			socket = null;
			process = null;
			return;
		}
		
		socket = s;

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
		Process p;
		try {
			LOGGER.info("In: {}, executing: {}", dir.getCanonicalPath(), Joiner.on(' ').join(toExec));
			p = pb.start();
		} catch (IOException e) {
			if (failing != null) {
				failing.failed(new IOException("Could not run tcpdump", e));
			}
			p = null;
		}
		
		if (p == null) {
			socket.close();
			socket = null;
			process = null;
			return;
		}
		
		process = p;

		final InputStream error = p.getErrorStream();
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
					LOGGER.error("Error in tcpdump process", e);
				}
			}
		});
		
		final InputStream input = p.getInputStream();
		execute("in", new Runnable() {
			@Override
			public void run() {
				try {
					try {
						tcpdumpReader.read(input, new TcpdumpReader.Handler() {
							@Override
							public void handle(double timestamp, Address sourceAddress, Address destinationAddress, ByteBuffer buffer) {
								if (receiver != null) {
									receiver.received(sourceAddress, buffer);
								}
							}
						});
					} finally {
						input.close();
					}
				} catch (IOException e) {
					LOGGER.error("Error in tcpdump process", e);
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
					if (failing != null) {
						failing.failed(new IOException("Non zero return code from tcpdump: " + code));
					}
				} else {
					if (closing != null) {
						closing.closed();
					}
				}
			}
		});
		
		if (connecting != null) {
			connecting.connected();
		}
	}
	
	@Override
	public void close() {
		if (process != null) {
			process.destroy();
		}
		if (socket != null) {
			socket.close();
		}
	}
	
	@Override
	public Connector send(Address address, ByteBuffer buffer) {
		if (socket == null) {
			return this;
		}
		
		LOGGER.trace("Sending datagram to: {}", address);
		try {
			DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.capacity(), InetAddress.getByName(address.host), address.port);
			socket.send(packet);
		} catch (IOException ioe) {
			LOGGER.trace("Error while sending datagram to: {}", address, ioe);
		}
		return this;
	}
}
