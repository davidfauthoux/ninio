package com.davfx.ninio.core;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.LittleEndianDataInputStream;

public final class RawTcpdumpReader implements TcpdumpReader {
	private static final Logger LOGGER = LoggerFactory.getLogger(RawTcpdumpReader.class);
	
	private static final int MAX_SIZE = 100 * 1024;

	private final boolean any;
	
	public RawTcpdumpReader(boolean any) {
		this.any = any;
	}
	
	@Override
	public Iterable<String> tcpdumpOptions() {
		return Arrays.asList("-w", "-", "-U");
	}

	/*%%%
	private static String macToString(byte[] mac) {
		StringBuilder sb = new StringBuilder(18);
	    for (byte b : mac) {
	        if (sb.length() > 0)
	            sb.append(':');
	        sb.append(String.format("%02x", b));
	    }
	    return sb.toString();
	}
	*/

	@Override
	public void read(InputStream input, Handler handler) throws IOException {
		@SuppressWarnings("resource")
		LittleEndianDataInputStream in = new LittleEndianDataInputStream(input);
		LOGGER.debug("Reading tcpdump stream");
		long header = in.readInt() & 0xFFFFFFFF;
		if (header != 0xA1B2C3D4) {
			throw new IOException("Bad header: 0x" + Long.toHexString(header));
		}
		int majorVersionNumber = in.readUnsignedShort();
		int minorVersionNumber = in.readUnsignedShort();
		long zone = in.readInt() & 0xFFFFFFFF;
		@SuppressWarnings("unused")
		long timestampAccuracy = in.readInt() & 0xFFFFFFFF;
		long maxCaptureLength = in.readInt() & 0xFFFFFFFF;
		long dataLinkType = in.readInt() & 0xFFFFFFFF;
		LOGGER.debug("Tcpdump header recognized (version = {}/{}, zone = {}, maxCaptureLength = {}, dataLinkType = {})", majorVersionNumber, minorVersionNumber, zone, maxCaptureLength, dataLinkType);
		
		/*
		https://wiki.wireshark.org/Development/LibpcapFileFormat
		
		typedef struct pcap_hdr_s {
			guint32 magic_number;   /* magic number * /
			guint16 version_major;  /* major version number * /
			guint16 version_minor;  /* minor version number * /
			gint32  thiszone;       /* GMT to local correction * /
			guint32 sigfigs;        /* accuracy of timestamps * /
			guint32 snaplen;        /* max length of captured packets, in octets * /
			guint32 network;        /* data link type * /
		} pcap_hdr_t;
		*/

		while (true) {
			try {
				//%% LOGGER.debug("Tcpdump loop, step 1");
				/*
				typedef struct pcaprec_hdr_s {
					guint32 ts_sec;         /* timestamp seconds * /
					guint32 ts_usec;        /* timestamp microseconds * /
					guint32 incl_len;       /* number of octets of packet saved in file * /
					guint32 orig_len;       /* actual length of packet * /
				} pcaprec_hdr_t;
				*/
				long timestampSeconds = in.readInt() & 0xFFFFFFFF;
				long timestampMicroSeconds = in.readInt() & 0xFFFFFFFF;
				@SuppressWarnings("unused")
				int savedLength = in.readInt();
				int actualLength = in.readInt();
				
				double timestamp = ((double) timestampSeconds) + (((double) timestampMicroSeconds) / 1_000_000d); // sec, usec
	
				byte[] destinationMac = new byte[6];
				byte[] sourceMac = new byte[6];
				in.readFully(destinationMac);
				in.readFully(sourceMac);
				
				//%% System.out.println("mac0="+macToString(destinationMac));
				//%% System.out.println("mac1="+macToString(sourceMac));
			
				//IPV4 UNICAST: 0800
				//IPV6 UNICAST: 86dd
				//IPV4 MULTICAST: 00000800
				//IPV6 MULTICAST: 000086dd
				byte[] unused = new byte[any ? 4 : 2];
				in.readFully(unused);
				
				LOGGER.trace("Tcpdump packet ({} bytes)", actualLength);
	
				int l = actualLength - destinationMac.length - sourceMac.length - unused.length;
				
				if (l > MAX_SIZE) {
					throw new IOException("Packet too big: " + l);
				}

				byte[] bytes = new byte[l];
				in.readFully(bytes);
				IpPacketReadUtils.read(timestamp, bytes, 0, bytes.length, handler);
			} catch (EOFException eof) {
				break;
			}
		}
	}
}
