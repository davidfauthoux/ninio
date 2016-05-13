package com.savarese.rocksaw.net;

import java.io.IOException;
import java.net.InetAddress;

class Pinger4 {
	private static final int TIMEOUT = 0;

	protected RawSocket socket;
	protected ICMPEchoPacket sendPacket, recvPacket;
	protected int offset, length, dataOffset;
	protected int requestType, replyType;
	protected byte[] sendData, recvData, srcAddress;
	protected int sequence, identifier;
	protected EchoReplyListener listener;

	public Pinger4(int id, int protocolFamily, int protocol) throws IOException {
		sequence = 0;
		identifier = id;
		setEchoReplyListener(null);

		sendPacket = new ICMPEchoPacket(1);
		recvPacket = new ICMPEchoPacket(1);
		sendData = new byte[84];
		recvData = new byte[84];

		sendPacket.setData(sendData);
		recvPacket.setData(recvData);
		sendPacket.setIPHeaderLength(5);
		recvPacket.setIPHeaderLength(5);
		sendPacket.setICMPDataByteLength(56);
		recvPacket.setICMPDataByteLength(56);

		offset = sendPacket.getIPHeaderByteLength();
		dataOffset = offset + sendPacket.getICMPHeaderByteLength();
		length = sendPacket.getICMPPacketByteLength();

		socket = new RawSocket();
		socket.open(protocolFamily, protocol);

		try {
			socket.setSendTimeout(TIMEOUT);
			socket.setReceiveTimeout(TIMEOUT);
		} catch (java.net.SocketException se) {
			socket.setUseSelectTimeout(true);
			socket.setSendTimeout(TIMEOUT);
			socket.setReceiveTimeout(TIMEOUT);
		}
	}

	public Pinger4(int id) throws IOException {
		this(id, RawSocket.PF_INET, RawSocket.getProtocolByName("icmp"));

		srcAddress = new byte[4];
		requestType = ICMPPacket.TYPE_ECHO_REQUEST;
		replyType = ICMPPacket.TYPE_ECHO_REPLY;
	}

	protected void computeSendChecksum(InetAddress host) throws IOException {
		sendPacket.computeICMPChecksum();
	}

	public void setEchoReplyListener(EchoReplyListener l) {
		listener = l;
	}

	/**
	 * Closes the raw socket opened by the constructor. After calling this
	 * method, the object cannot be used.
	 */
	public void close() throws IOException {
		socket.close();
	}

	public void sendEchoRequest(InetAddress host) throws IOException {
		sendPacket.setType(requestType);
		sendPacket.setCode(0);
		sendPacket.setIdentifier(identifier);
		sendPacket.setSequenceNumber(sequence++);

		OctetConverter.longToOctets(System.nanoTime(), sendData, dataOffset);

		computeSendChecksum(host);

		socket.write(host, sendData, offset, length);
	}

	public void receive() throws IOException {
		socket.read(recvData, srcAddress);
	}

	public void receiveEchoReply() throws IOException {
		do {
			receive();
		} while (recvPacket.getType() != replyType || recvPacket.getIdentifier() != identifier);

		if (listener != null)
			listener.notifyEchoReply(recvPacket, recvData, dataOffset, srcAddress);
	}

	/**
	 * Issues a synchronous ping.
	 *
	 * @param host
	 *            The host to ping.
	 * @return The round trip time in nanoseconds.
	 */
	public long ping(InetAddress host) throws IOException {
		sendEchoRequest(host);
		receiveEchoReply();

		long end = System.nanoTime();
		long start = OctetConverter.octetsToLong(recvData, dataOffset);

		return (end - start);
	}

	/**
	 * @return The number of bytes in the data portion of the ICMP ping request
	 *         packet.
	 */
	public int getRequestDataLength() {
		return sendPacket.getICMPDataByteLength();
	}

	/** @return The number of bytes in the entire IP ping request packet. */
	public int getRequestPacketLength() {
		return sendPacket.getIPPacketLength();
	}
}
