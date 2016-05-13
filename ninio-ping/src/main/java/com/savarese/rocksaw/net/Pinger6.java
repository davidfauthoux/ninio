package com.savarese.rocksaw.net;

import java.io.IOException;
import java.net.InetAddress;

class Pinger6 extends Pinger4 {
	private static final int IPPROTO_ICMPV6 = 58;
	private static final int ICMPv6_TYPE_ECHO_REQUEST = 128;
	private static final int ICMPv6_TYPE_ECHO_REPLY = 129;

	/**
	 * Operating system kernels are supposed to calculate the ICMPv6 checksum
	 * for the sender, but Microsoft's IPv6 stack does not do this. Nor does it
	 * support the IPV6_CHECKSUM socket option. Therefore, in order to work on
	 * the Windows family of operating systems, we have to calculate the ICMPv6
	 * checksum.
	 */
	private static class ICMPv6ChecksumCalculator extends IPPacket {
		ICMPv6ChecksumCalculator() {
			super(1);
		}

		private int computeVirtualHeaderTotal(byte[] destination, byte[] source, int icmpLength) {
			int total = 0;

			for (int i = 0; i < source.length;)
				total += (((source[i++] & 0xff) << 8) | (source[i++] & 0xff));
			for (int i = 0; i < destination.length;)
				total += (((destination[i++] & 0xff) << 8) | (destination[i++] & 0xff));

			total += (icmpLength >>> 16);
			total += (icmpLength & 0xffff);
			total += IPPROTO_ICMPV6;

			return total;
		}

		int computeChecksum(byte[] data, ICMPPacket packet, byte[] destination, byte[] source) {
			int startOffset = packet.getIPHeaderByteLength();
			int checksumOffset = startOffset + ICMPEchoPacket.OFFSET_ICMP_CHECKSUM;
			int ipLength = packet.getIPPacketLength();
			int icmpLength = packet.getICMPPacketByteLength();

			setData(data);

			return _computeChecksum_(startOffset, checksumOffset, ipLength, computeVirtualHeaderTotal(destination, source, icmpLength), true);
		}
	}

	private byte[] localAddress;
	private ICMPv6ChecksumCalculator icmpv6Checksummer;

	public Pinger6(int id) throws IOException {
		super(id, RawSocket.PF_INET6, IPPROTO_ICMPV6 /*getProtocolByName("ipv6-icmp")*/);

		icmpv6Checksummer = new ICMPv6ChecksumCalculator();
		srcAddress = new byte[16];
		localAddress = new byte[16];
		requestType = ICMPv6_TYPE_ECHO_REQUEST;
		replyType = ICMPv6_TYPE_ECHO_REPLY;
	}

	protected void computeSendChecksum(InetAddress host) throws IOException {
		// This is necessary only for Windows, which doesn't implement
		// RFC 2463 correctly.
		socket.getSourceAddressForDestination(host, localAddress);
		icmpv6Checksummer.computeChecksum(sendData, sendPacket, host.getAddress(), localAddress);
	}

	public void receive() throws IOException {
		socket.read(recvData, offset, length, srcAddress);
	}

	public int getRequestPacketLength() {
		return (getRequestDataLength() + 40);
	}
}
