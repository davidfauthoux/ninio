package com.savarese.rocksaw.net;

import java.io.IOException;

public interface EchoReplyListener {
	void notifyEchoReply(ICMPEchoPacket packet, byte[] data, int dataOffset, byte[] srcAddress) throws IOException;
}
