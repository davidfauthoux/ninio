package com.davfx.ninio.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.ClassThreadFactory;
import com.davfx.ninio.util.DateUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

// On MacOS X: sudo chmod go=r /dev/bpf*
public final class TcpdumpSupervise {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TcpdumpSupervise.class);
	
	private static double floorTime(double now, double period) {
    	double precision = 1000d;
    	long t = (long) (now * precision);
    	long d = (long) (period * precision);
    	return (t - (t % d)) / precision;
	}
	
	private static String percent(long out, long in) {
		return String.format("%.2f", ((double) (out - in)) * 100d / ((double) out));
	}

	private static final AtomicLong inPackets = new AtomicLong(0L);
	private static final AtomicLong outPackets = new AtomicLong(0L);
	private static final AtomicLong inBytes = new AtomicLong(0L);
	private static final AtomicLong outBytes = new AtomicLong(0L);
		
	private static void incIn(long bytes) {
		inPackets.incrementAndGet();
		inBytes.addAndGet(bytes);
	}
	private static void incOut(long bytes) {
		outPackets.incrementAndGet();
		outBytes.addAndGet(bytes);
	}

	private static void execute(String name, Runnable runnable) {
		new ClassThreadFactory(TcpdumpSocket.class, name).newThread(runnable).start();
	}
	
	public static void main(String[] args) throws Exception {
		String tcpdumpCommand = System.getProperty("tcpdump", "tcpdump");
		String interfaceId = System.getProperty("interface", "eth1");
		String rule = System.getProperty("rule", "port 161");
		String modeString = System.getProperty("mode", "raw");
		String hereHostString = System.getProperty("here", "");
		
		/*
		interfaceId = "en1";
		rule = "port 161";
		modeString = "hex";
		hereHostString = "192.168.3.238";
		*/

		if (hereHostString.isEmpty()) {
			throw new Exception("-Dhere=<IP> required");
		}
		
		TcpdumpMode mode;
		if (modeString.equals("hex")) {
			mode = TcpdumpMode.HEX;
		} else if (modeString.equals("raw")) {
			mode = TcpdumpMode.RAW;
		} else {
			throw new Exception("Bad mode (only hex|raw allowed): " + modeString);
		}

		final byte[] hereHost = InetAddress.getByName(hereHostString).getAddress();

		double supervisionClear = Double.parseDouble(System.getProperty("clear", "300"));
		double supervisionDisplay = Double.parseDouble(System.getProperty("display", "10"));
		
		//
		
		double now = DateUtils.now();
		double startDisplay = supervisionDisplay - (now - floorTime(now, supervisionDisplay));
		double startClear = supervisionClear - (now - floorTime(now, supervisionClear));
		
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ClassThreadFactory(TcpdumpSupervise.class, true));

		executor.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				long ip = inPackets.get();
				long ib = inBytes.get();
				long op = outPackets.get();
				long ob = outBytes.get();
				LOGGER.debug("out = {} ({} Kb), in = {} ({} Kb), lost = {} ({} %)", op, ob / 1000d, ip, ib / 1000d, op - ip, percent(op, ip));
			}
		}, (long) (startDisplay * 1000d), (long) (supervisionDisplay * 1000d), TimeUnit.MILLISECONDS);

		executor.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				long ip = inPackets.getAndSet(0L);
				long ib = inBytes.getAndSet(0L);
				long op = outPackets.getAndSet(0L);
				long ob = outBytes.getAndSet(0L);
				LOGGER.info("(cleared) out = {} ({} Kb), in = {} ({} Kb), lost = {} ({} %)", op, ob / 1000d, ip, ib / 1000d, op - ip, percent(op, ip));
			}
		}, (long) (startClear * 1000d), (long) (supervisionClear * 1000d), TimeUnit.MILLISECONDS);

		//
		
		final TcpdumpReader tcpdumpReader = (mode == TcpdumpMode.RAW) ? new RawTcpdumpReader(interfaceId.equals("any")) : new HexTcpdumpReader();
		
		File dir = new File(".");

		List<String> toExec = new LinkedList<String>();
		toExec.add(tcpdumpCommand);
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
		
		LOGGER.info("Executing: {}", Joiner.on(' ').join(toExec));
		
		ProcessBuilder pb = new ProcessBuilder(toExec);
		pb.directory(dir);
		Process process = pb.start();

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
								if (Arrays.equals(sourceAddress.ip, hereHost)) {
									incOut(buffer.remaining());
								} else if (Arrays.equals(destinationAddress.ip, hereHost)) {
									incIn(buffer.remaining());
								}
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

		int code = process.waitFor();

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
			new IOException("Non zero return code from tcpdump: " + code);
		}
	}
}
