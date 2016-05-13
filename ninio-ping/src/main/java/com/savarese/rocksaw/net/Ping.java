package com.savarese.rocksaw.net;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Ping implements AutoCloseable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Ping.class);
	
	private static final double WAIT_TIME_ON_ERROR = 1d;
	
	private final ExecutorService executor4 = Executors.newSingleThreadExecutor(new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			return new Thread(r, "Ping4");
		}
	});
	private final ExecutorService executor6 = Executors.newSingleThreadExecutor(new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			return new Thread(r, "Ping6");
		}
	});
	
	private final Pinger4 pinger4;
	private final Pinger4 pinger6;
	
	private volatile boolean closed = false;

	public Ping(final PingHandler handler) throws IOException {
		pinger4 = new Pinger4(65535);
		try {
			pinger6 = new Pinger6(65535);
		} catch (IOException e) {
			try {
				pinger4.close();
			} catch (IOException ce) {
			}
			throw e;
		}

		pinger4.setEchoReplyListener(new EchoReplyListener() {
			public void notifyEchoReply(ICMPEchoPacket packet, byte[] data, int dataOffset, byte[] srcAddress) throws IOException {
				long end = System.nanoTime();
				long start = OctetConverter.octetsToLong(data, dataOffset);
				// Note: Java and JNI overhead will be noticeable (100-200
				// microseconds) for sub-millisecond transmission times.
				// The first ping may even show several seconds of delay
				// because of initial JIT compilation overhead.
				double rtt = (double) ((end - start) / 1_000_000_000d);
				double ttl = packet.getTTL();
				
				handler.pong(InetAddress.getByAddress(srcAddress), rtt, ttl);
			}
		});
		pinger6.setEchoReplyListener(new EchoReplyListener() {
			public void notifyEchoReply(ICMPEchoPacket packet, byte[] data, int dataOffset, byte[] srcAddress) throws IOException {
				long end = System.nanoTime();
				long start = OctetConverter.octetsToLong(data, dataOffset);
				// Note: Java and JNI overhead will be noticeable (100-200
				// microseconds) for sub-millisecond transmission times.
				// The first ping may even show several seconds of delay
				// because of initial JIT compilation overhead.
				double rtt = (double) ((end - start) / 1_000_000_000d);
				double ttl = packet.getTTL();

				handler.pong(InetAddress.getByAddress(srcAddress), rtt, ttl);
			}
		});

		executor4.execute(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						pinger4.receiveEchoReply();
					} catch (IOException e) {
						if (closed) {
							break;
						}
						LOGGER.error("Could not receive IPv4 ping reply", e);
						try {
							Thread.sleep((long) (WAIT_TIME_ON_ERROR * 1000d));
						} catch (InterruptedException ie) {
						}
					}
				}
			}
		});
		executor6.execute(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						pinger6.receiveEchoReply();
					} catch (IOException e) {
						if (closed) {
							break;
						}
						LOGGER.error("Could not receive IPv6 ping reply", e);
						try {
							Thread.sleep((long) (WAIT_TIME_ON_ERROR * 1000d));
						} catch (InterruptedException ie) {
						}
					}
				}
			}
		});
	}

	@Override
	public void close() {
		closed = true;
		try {
			pinger4.close();
		} catch (IOException e) {
		}
		try {
			pinger6.close();
		} catch (IOException e) {
		}
		executor4.shutdown();
		executor6.shutdown();
	}
	
	public void ping(final InetAddress address) throws IOException {
		if (address instanceof Inet6Address) {
			pinger6.sendEchoRequest(address);
		} else {
			pinger4.sendEchoRequest(address);
		}
	}
}
