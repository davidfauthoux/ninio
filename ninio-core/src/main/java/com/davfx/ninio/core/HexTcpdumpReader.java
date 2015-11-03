package com.davfx.ninio.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;

final class HexTcpdumpReader implements TcpdumpReader {
	private static final Logger LOGGER = LoggerFactory.getLogger(HexTcpdumpReader.class);
	
	public HexTcpdumpReader() {
	}

	@Override
	public Iterable<String> tcpdumpOptions() {
		return Arrays.asList("-x", "-tt");
	}
	
	@Override
	public void read(InputStream input, int maxSize, Handler handler) throws IOException {
		BufferedReader r = new BufferedReader(new InputStreamReader(input, Charsets.US_ASCII));
		byte[] bytes = null;
		int bytesIndex = 0;
		double timestamp = 0d;
		long lineCount = 0L;
		while (true) {
			String line = r.readLine();
			if (line == null) {
				break;
			}
			
			lineCount++;
			
			try {
				if (line.charAt(0) == '\t') {
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
					continue;
				} else {
					if (bytes != null) {
						IpPacketReadUtils.read(timestamp, bytes, 0, bytesIndex, handler);
						bytes = null;
					}
				}
				
				int i = line.indexOf(' ');
				timestamp = Double.parseDouble(line.substring(0,  i));
				bytes = new byte[maxSize];
				bytesIndex = 0;
			} catch (Exception e) {
				LOGGER.error("Error on line #{}: {}", lineCount, line, e);
				bytes = null;
			}
		}
	}
}
