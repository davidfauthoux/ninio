package com.davfx.ninio.common;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.nio.ByteBuffer;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class IpPacketReadUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(IpPacketReadUtils.class);
	
	private IpPacketReadUtils() {
	}
	
	public static void read(double timestamp, byte[] bytes, int off, int length, TcpdumpReader.Handler handler) throws IOException {
		ByteBuffer b = ByteBuffer.wrap(bytes, off, length);
		int firstByte = b.get() & 0xFF;
		int ipVersion = firstByte >> 4;
		int packetType;
		String sourceIp;
		String destinationIp;
		int payloadLength;
		if (ipVersion == 4) {
			int headerLength = (firstByte & 0x0F) * 4;
			@SuppressWarnings("unused")
			int tos = b.get() & 0xFF;
			int totalLength = b.getShort() & 0xFFFF;
			payloadLength = totalLength - headerLength;
			@SuppressWarnings("unused")
			int identification = b.getShort() & 0xFFFF;
			@SuppressWarnings("unused")
			int indicatorAndFragmentOffset = b.getShort() & 0xFFFF;
			@SuppressWarnings("unused")
			int ttl = b.get() & 0xFF;
			int protocol = (b.get() & 0xFF);
			packetType = protocol;
			@SuppressWarnings("unused")
			int checksum = b.getShort() & 0xFFFF;
			byte[] sourceIpBytes = new byte[4];
			b.get(sourceIpBytes);
			sourceIp = Inet4Address.getByAddress(sourceIpBytes).getHostAddress();
			byte[] destinationIpBytes = new byte[4];
			b.get(destinationIpBytes);
			destinationIp = Inet4Address.getByAddress(destinationIpBytes).getHostAddress();
		} else if (ipVersion == 6) {
			// traffic class (low bits) and flow label 
			b.get(); b.get(); b.get();
			payloadLength = b.getShort() & 0xFFFF;
			int nextHeader = (b.get() & 0xFF);
			packetType = nextHeader;
			@SuppressWarnings("unused")
			int hopLimit = b.get() & 0xFF;

			byte[] sourceIpBytes = new byte[16];
			b.get(sourceIpBytes);
			sourceIp = Inet6Address.getByAddress(sourceIpBytes).getHostAddress();
			byte[] destinationIpBytes = new byte[16];
			b.get(destinationIpBytes);
			destinationIp = Inet6Address.getByAddress(destinationIpBytes).getHostAddress();
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
			if (udpLength != payloadLength) {
				LOGGER.warn("Strange packet, udp length {} should equal payload length {}", udpLength, payloadLength);
			}
			int packetLength = payloadLength - 8; // udpLength SHOULD EQUAL payloadLength
			
			Address sourceAddress = new Address(sourceIp, sourcePort);
			Address destinationAddress = new Address(destinationIp, destinationPort);
			
			LOGGER.trace("Packet received: {} -> {} {}", sourceAddress, destinationAddress, new Date((long) (timestamp * 1000d)));

			handler.handle(timestamp, sourceAddress, destinationAddress, ByteBuffer.wrap(bytes, packetPosition, packetLength));
		}
	}
}
