package com.davfx.ninio.snmp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Version2cPacketParser {
	/*%%%
	public static void main(String[] args) throws Exception {
		try (DataInputStream in = new DataInputStream(new FileInputStream(new File("tcpdump.output.bin")))) {
			while (true) {
				int l = in.readInt();
				System.out.println("l=" +l);
				byte[] d = new byte[l];
				in.readFully(d);
				try {
					new Version2cPacketParser(ByteBuffer.wrap(d));
				} catch (Exception e) {
					e.printStackTrace(System.out);
				}
			}
		}
	}
	*/

	private static final Logger LOGGER = LoggerFactory.getLogger(Version2cPacketParser.class);

	private final int requestId;
	private final int errorStatus;
	private final int errorIndex;
	private final List<Result> results = new LinkedList<Result>();

	public Version2cPacketParser(ByteBuffer buffer) throws IOException {
		BerReader ber = new BerReader(buffer);
		ber.beginReadSequence();
		{
			int version = ber.readInteger();
			if (version != BerConstants.VERSION_2C) {
				throw new IOException("Invalid version: " + version + " should be " + BerConstants.VERSION_2C);
			}
			ber.readBytes(); // community
			
			int s = ber.beginReadSequence();
			{
				if (s != BerConstants.RESPONSE) {
					throw new IOException("Not a response packet");
				}
				requestId = ber.readInteger();
				errorStatus = ber.readInteger();
				errorIndex = ber.readInteger();

				ber.beginReadSequence();
				{
					while (ber.hasRemainingInSequence()) {
						ber.beginReadSequence();
						{
							Oid oid = ber.readOid();
							String value = ber.readValue();
							if (value == null) {
								LOGGER.trace("Opaque value: {}", oid);
							}
							// OidValue value = ber.readOidValue();
							results.add(new Result(oid, value));
						}
						ber.endReadSequence();
					}
				}
				ber.endReadSequence();
			}
			ber.endReadSequence();
		}
		ber.endReadSequence();
	}

	public Iterable<Result> getResults() {
		return results;
	}

	public int getRequestId() {
		return requestId;
	}
	public int getErrorStatus() {
		return errorStatus;
	}
	public int getErrorIndex() {
		return errorIndex;
	}
}
