package com.davfx.ninio.snmp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.InMemoryCache;

//Transforms all values to string
//Does not tell community on response
public final class SnmpInMemoryCacheInterpreter implements InMemoryCache.Interpreter<Integer> {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpInMemoryCacheInterpreter.class);
	
	public SnmpInMemoryCacheInterpreter() {
	}
	
	@Override
	public InMemoryCache.Context<Integer> handleRequest(ByteBuffer packet) {
		try {
			BerReader ber = new BerReader(packet);
			ber.beginReadSequence();
			{
				int version = ber.readInteger();
				if (version == BerConstants.VERSION_2C) {
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
									
									return new InMemoryCache.Context<Integer>(key, requestId);
								}
								// ber.endReadSequence();
							}
						}
						ber.endReadSequence();
					}
					ber.endReadSequence();
				} else if (version == BerConstants.VERSION_3) {
					// LOGGER.info("handleRequest VERSION_3");
					byte securityFlags;
		
					ber.beginReadSequence();
					{
						ber.readInteger(); // Packet number
						ber.readInteger(); // Max packet size
						securityFlags = ber.readBytes().get();
						int securityModel = ber.readInteger();
						if (securityModel != BerConstants.VERSION_3_USM_SECURITY_MODEL) {
							// LOGGER.info("securityModel != BerConstants.VERSION_3_USM_SECURITY_MODEL");
							return null;
						}
					}
					ber.endReadSequence();
		
					BerReader secBer = new BerReader(ber.readBytes());
					secBer.beginReadSequence();
					{
						secBer.readBytes(); // engine
						secBer.readInteger();
						secBer.readInteger();
						secBer.readBytes(); // login
						secBer.readBytes();
						secBer.readBytes(); // decryptParams
					}
					secBer.endReadSequence();
		
					BerReader pdu;
					if ((securityFlags & BerConstants.VERSION_3_PRIV_FLAG) != 0) {
						// LOGGER.info("(securityFlags & BerConstants.VERSION_3_PRIV_FLAG) != 0");
						return null; // Will not decrypt packet
					} else {
						pdu = ber;
					}
		
					pdu.beginReadSequence();
					pdu.readBytes();
					pdu.readBytes();
		
					int type = pdu.beginReadSequence();
					{
						int requestId = pdu.readInteger();
						pdu.readInteger();
						pdu.readInteger();
		
						pdu.beginReadSequence();
						{
							while (pdu.hasRemainingInSequence()) {
								pdu.beginReadSequence();
								{
									Oid oid = pdu.readOid();
									pdu.readValue();
									
									String key = oid + "/" + type;
									// LOGGER.info("key={}", key);

									return new InMemoryCache.Context<Integer>(key, requestId);
								}
								// pdu.endReadSequence();
							}
						}
						pdu.endReadSequence();
					}
					pdu.endReadSequence();
				} else {
					return null;
				}
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
				if (version == BerConstants.VERSION_2C) {
					ber.readBytes(); // community
					
					ber.beginReadSequence();
					{
						int requestId = ber.readInteger();
						return requestId;
					}
					// ber.endReadSequence();
				} else if (version == BerConstants.VERSION_3) {
					// LOGGER.info("handleResponse VERSION_3");
					byte securityFlags;
					
					ber.beginReadSequence();
					{
						ber.readInteger(); // Packet number
						ber.readInteger(); // Max packet size
						securityFlags = ber.readBytes().get();
						ber.readInteger(); // securityModel
					}
					ber.endReadSequence();
		
					BerReader secBer = new BerReader(ber.readBytes());
					secBer.beginReadSequence();
					{
						secBer.readBytes(); // engine
						secBer.readInteger();
						secBer.readInteger();
						secBer.readBytes(); // login
						secBer.readBytes();
						secBer.readBytes(); // decryptParams
					}
					secBer.endReadSequence();
		
					BerReader pdu;
					if ((securityFlags & BerConstants.VERSION_3_PRIV_FLAG) != 0) {
						// LOGGER.info("(securityFlags & BerConstants.VERSION_3_PRIV_FLAG) != 0");
						return null; // Could not handle encrypted packet
					} else {
						pdu = ber;
					}
		
					pdu.beginReadSequence();
					pdu.readBytes();
					pdu.readBytes();
		
					int s = pdu.beginReadSequence();
					{
						if ((s != BerConstants.REPORT) && (s != BerConstants.RESPONSE)) {
							throw new IOException("Not a response packet");
						}
						int requestId = ber.readInteger();
						// LOGGER.info("requestId={}", requestId);
						return requestId;
					}
					// ber.endReadSequence();
				} else {
					return null;
				}
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
				if (version == BerConstants.VERSION_2C) {
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
				} else if (version == BerConstants.VERSION_3) {
					// LOGGER.info("transform VERSION_3");
					byte securityFlags;
					
					ber.beginReadSequence();
					{
						ber.readInteger(); // Packet number
						ber.readInteger(); // Max packet size
						securityFlags = ber.readBytes().get();
						int securityModel = ber.readInteger();
						if (securityModel != BerConstants.VERSION_3_USM_SECURITY_MODEL) {
							// LOGGER.info("securityModel != BerConstants.VERSION_3_USM_SECURITY_MODEL");
							return null;
						}
					}
					ber.endReadSequence();
		
					BerReader secBer = new BerReader(ber.readBytes());
					secBer.beginReadSequence();
					{
						secBer.readBytes(); // engine
						secBer.readInteger();
						secBer.readInteger();
						secBer.readBytes(); // login
						secBer.readBytes();
						secBer.readBytes(); // decryptParams
					}
					secBer.endReadSequence();
		
					BerReader pdu;
					if ((securityFlags & BerConstants.VERSION_3_PRIV_FLAG) != 0) {
						// LOGGER.info("(securityFlags & BerConstants.VERSION_3_PRIV_FLAG) != 0");
						return null; // Will not decrypt packet
					} else {
						pdu = ber;
					}
		
					pdu.beginReadSequence();
					pdu.readBytes();
					pdu.readBytes();
		
					int s = pdu.beginReadSequence();
					{
						if ((s != BerConstants.REPORT) && (s != BerConstants.RESPONSE)) {
							throw new IOException("Not a response packet");
						}
						pdu.readInteger();
						errorStatus = pdu.readInteger();
						errorIndex = pdu.readInteger();
		
						pdu.beginReadSequence();
						{
							while (pdu.hasRemainingInSequence()) {
								pdu.beginReadSequence();
								{
									Oid oid = pdu.readOid();
									String value = pdu.readValue();
									if (value != null) {
										results.add(new SnmpResult(oid, value));
									}
								}
								pdu.endReadSequence();
							}
						}
						pdu.endReadSequence();
					}
					pdu.endReadSequence();
				} else {
					return null;
				}
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
