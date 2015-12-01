package com.davfx.ninio.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;

public final class HexTcpdumpReader implements TcpdumpReader {
	private static final Logger LOGGER = LoggerFactory.getLogger(HexTcpdumpReader.class);
	
	private static final int MAX_SIZE = 100 * 1024;
	
	public HexTcpdumpReader() {
	}

	@Override
	public Iterable<String> tcpdumpOptions() {
		return Arrays.asList("-x", "-tt", "-l", "-v");
	}
	
	@Override
	public void read(InputStream input, Handler handler) throws IOException {
		LOGGER.trace("Reading hex tcpdump");
		BufferedReader r = new BufferedReader(new InputStreamReader(input, Charsets.US_ASCII));
		byte[] bytes = null;
		int bytesIndex = 0;
		double timestamp = 0d;
		long lineCount = 0L;
		while (true) {
			LOGGER.trace("Reading hex line...");
			String line = r.readLine();
			if (line == null) {
				LOGGER.debug("End");
				break;
			}
			
			LOGGER.trace("Hex line: {}", line);
			
			lineCount++;
			
			try {
				char first = line.charAt(0);
				if (first == ' ') {
					// Ignored
					continue;
				} else if (first == '\t') {
					if (bytes == null) {
						continue;
					}
					int lineLength = line.length();
					int prefixLength = "\t0x0000:  ".length();
					boolean even = false;
					for (int i = prefixLength; i < lineLength; i++) {
						char c = line.charAt(i);
						if (c != ' ') {
							byte b;
							if ((c >= '0') && (c <= '9')) {
								b = (byte) ((c - '0') & 0xFF);
							} else if ((c >= 'a') && (c <= 'f')) {
								b = (byte) ((c - 'a' + 10) & 0xFF);
							} else if ((c >= 'A') && (c <= 'F')) {
								b = (byte) ((c - 'A' + 10) & 0xFF);
							} else {
								b = 0;
							}

							if (!even) {
								b <<= 4;
							}
							bytes[bytesIndex] |= b;
							if (even) {
								bytesIndex++;
							}
							even = !even;
						}
					}
					if (bytesIndex == bytes.length) {
						IpPacketReadUtils.read(timestamp, bytes, 0, bytesIndex, handler);
						bytes = null;
					}
					continue;
				} else {
					if (bytes != null) {
						throw new IOException("Incomplete packet (" + bytesIndex + " < " + bytes.length + ")");
					}
				}
				
				int i = line.indexOf(' ');
				String lengthString = ", length ";
				int j = line.indexOf(lengthString);
				int k = line.indexOf(")", j + lengthString.length());
				timestamp = Double.parseDouble(line.substring(0,  i));
				int length = Integer.parseInt(line.substring(j + lengthString.length(),  k));
				if (length > MAX_SIZE) {
					throw new IOException("Packet too big: " + length);
				}
				bytes = new byte[length];
				bytesIndex = 0;
			} catch (Exception e) {
				LOGGER.error("Error on line #{}: {}", lineCount, line, e);
				bytes = null;
			}
		}
	}
}
