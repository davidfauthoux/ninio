package com.davfx.ninio.core;

import java.nio.ByteBuffer;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class IpPacketReadUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(IpPacketReadUtils.class);
	
	private IpPacketReadUtils() {
	}
	
	public static void read(double timestamp, byte[] bytes, int off, int length, TcpdumpReader.Handler handler) {
		ByteBuffer b = ByteBuffer.wrap(bytes, off, length);
		int firstByte = b.get() & 0xFF;
		int ipVersion = firstByte >> 4;
		int packetType;
		byte[] sourceIp;
		byte[] destinationIp;
		int payloadLength;
		if (ipVersion == 4) {
			int headerLength = (firstByte & 0x0F) * 4;
			@SuppressWarnings("unused")
			int tos = b.get() & 0xFF;
			int totalLength = b.getShort() & 0xFFFF;
			payloadLength = totalLength - headerLength;
			@SuppressWarnings("unused")
			int identification = b.getShort() & 0xFFFF;
			int indicatorAndFragmentOffset = b.getShort() & 0xFFFF;
			@SuppressWarnings("unused")
			int ttl = b.get() & 0xFF;
			int protocol = (b.get() & 0xFF);
			packetType = protocol;
			@SuppressWarnings("unused")
			int checksum = b.getShort() & 0xFFFF;
			sourceIp = new byte[4];
			b.get(sourceIp);
			destinationIp = new byte[4];
			b.get(destinationIp);
			if (indicatorAndFragmentOffset != 0) {
				LOGGER.warn("Fragmented packet from {}", Address.ipToString(sourceIp));
				return;
			}
		} else if (ipVersion == 6) {
			// traffic class (low bits) and flow label 
			b.get(); b.get(); b.get();
			payloadLength = b.getShort() & 0xFFFF;
			int nextHeader = (b.get() & 0xFF);
			packetType = nextHeader;
			@SuppressWarnings("unused")
			int hopLimit = b.get() & 0xFF;

			sourceIp = new byte[16];
			b.get(sourceIp);
			destinationIp = new byte[16];
			b.get(destinationIp);
		} else {
			packetType = -1;
			sourceIp = null;
			destinationIp = null;
			payloadLength = -1;
		}
		
		if (packetType == 17) {
			// UDP
			int sourcePort = b.getShort() & 0xFFFF;
			int destinationPort = b.getShort() & 0xFFFF;
			int udpLength = b.getShort() & 0xFFFF;
			@SuppressWarnings("unused")
			int checksum = b.getShort() & 0xFFFF;
			int packetPosition = b.position();
			int packetLength = payloadLength - 8; // udpLength SHOULD EQUAL payloadLength
			
			Address sourceAddress = new Address(sourceIp, sourcePort);
			Address destinationAddress = new Address(destinationIp, destinationPort);
			
			if (udpLength != payloadLength) {
				LOGGER.warn("Strange packet from {}, udp length {} should equal payload length {}", sourceAddress, udpLength, payloadLength);
				return;
			}
			LOGGER.trace("Packet received: {} -> {} {}", sourceAddress, destinationAddress, new Date((long) (timestamp * 1000d)));

			ByteBuffer bb;
			try {
				bb = ByteBuffer.wrap(bytes, packetPosition, packetLength);
			} catch (Exception e) {
				LOGGER.warn("Invalid packet: {} -> {} (position={}, length={}, data={})", sourceAddress, destinationAddress, packetPosition, packetLength, bytes.length, e);
				return;
			}
			
			handler.handle(timestamp, sourceAddress, destinationAddress, bb);
		}
	}
}
