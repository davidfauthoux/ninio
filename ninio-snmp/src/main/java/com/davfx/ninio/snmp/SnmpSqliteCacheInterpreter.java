package com.davfx.ninio.snmp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.SqliteCache;

//Transforms all values to string
//Does not tell community on response
public final class SnmpSqliteCacheInterpreter implements SqliteCache.Interpreter<Integer> {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpSqliteCacheInterpreter.class);
	
	public SnmpSqliteCacheInterpreter() {
	}
	
	@Override
	public SqliteCache.Context<Integer> handleRequest(ByteBuffer packet) {
		try {
			BerReader ber = new BerReader(packet);
			ber.beginReadSequence();
			{
				int version = ber.readInteger();
				if (version != BerConstants.VERSION_2C) {
					throw new IOException("Invalid version: " + version + " should be " + BerConstants.VERSION_2C);
				}
				ber.readBytes(); // community
				
				int type = ber.beginReadSequence();
				{
					int requestId = ber.readInteger();
					ber.readInteger();
					ber.readInteger();

					ber.beginReadSequence();
					{
						while (ber.hasRemainingInSequence()) {
							ber.beginReadSequence();
							{
								Oid oid = ber.readOid();
								ber.readValue();
								
								String key = oid + "/" + type;
								
								return new SqliteCache.Context<Integer>(key, requestId);
							}
							// ber.endReadSequence();
						}
					}
					ber.endReadSequence();
				}
				ber.endReadSequence();
			}
			ber.endReadSequence();
		} catch (IOException ioe) {
			LOGGER.error("Invalid request packet", ioe);
		}
		
		return null;
	}
	
	@Override
	public Integer handleResponse(ByteBuffer packet) {
		try {
			BerReader ber = new BerReader(packet);
			ber.beginReadSequence();
			{
				int version = ber.readInteger();
				if (version != BerConstants.VERSION_2C) {
					throw new IOException("Invalid version: " + version + " should be " + BerConstants.VERSION_2C);
				}
				ber.readBytes(); // community
				
				ber.beginReadSequence();
				{
					int requestId = ber.readInteger();
					return requestId;
				}
				// ber.endReadSequence();
			}
			// ber.endReadSequence();
		} catch (IOException ioe) {
			LOGGER.error("Invalid response packet", ioe);
		}
		
		return null;
	}
	
	@Override
	public ByteBuffer transform(ByteBuffer packet, Integer requestId) {
		LOGGER.trace("Transforming packet with requestId = {}", requestId);
		
		int errorStatus;
		int errorIndex;
		List<SnmpResult> results = new LinkedList<>();
		
		try {
			BerReader ber = new BerReader(packet);
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
					ber.readInteger();
					errorStatus = ber.readInteger();
					errorIndex = ber.readInteger();

					ber.beginReadSequence();
					{
						while (ber.hasRemainingInSequence()) {
							ber.beginReadSequence();
							{
								Oid oid = ber.readOid();
								String value = ber.readValue();
								if (value != null) {
									results.add(new SnmpResult(oid, value));
								}
							}
							ber.endReadSequence();
						}
					}
					ber.endReadSequence();
				}
				ber.endReadSequence();
			}
			ber.endReadSequence();
		} catch (IOException ioe) {
			LOGGER.error("Invalid response packet", ioe);
			return null;
		}

		//
		
		SequenceBerPacket oidSequence = new SequenceBerPacket(BerConstants.SEQUENCE);
		for (SnmpResult r : results) {
			oidSequence.add(new SequenceBerPacket(BerConstants.SEQUENCE)
				.add(new OidBerPacket(r.oid))
				.add(new BytesBerPacket(BerPacketUtils.bytes(r.value))));
		}

		SequenceBerPacket root = new SequenceBerPacket(BerConstants.SEQUENCE)
		.add(new IntegerBerPacket(BerConstants.VERSION_2C))
		.add(new BytesBerPacket(BerPacketUtils.bytes(""))) // community ignored
		.add(new SequenceBerPacket(BerConstants.RESPONSE)
			.add(new IntegerBerPacket(requestId))
			.add(new IntegerBerPacket(errorStatus))
			.add(new IntegerBerPacket(errorIndex))
			.add(oidSequence));

		ByteBuffer buffer = ByteBuffer.allocate(BerPacketUtils.typeAndLengthBufferLength(root.lengthBuffer()) + root.length());
		root.write(buffer);
		buffer.flip();

		return buffer;
	}
}
