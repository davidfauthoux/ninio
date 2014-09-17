package com.davfx.ninio.snmp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

final class Version2cPacketParser {

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
			if (ber.beginReadSequence() != BerConstants.RESPONSE) {
				throw new IOException("Not a response packet");
			}
			{
				requestId = ber.readInteger();
				errorStatus = ber.readInteger();
				errorIndex = ber.readInteger();

				ber.beginReadSequence();
				{
					while (ber.hasRemainingInSequence()) {
						ber.beginReadSequence();
						{
							Oid oid = ber.readOid();
							OidValue value = ber.readOidValue();
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
