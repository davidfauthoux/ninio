package com.davfx.ninio.snmp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Version3PacketParser {

	private static final Logger LOGGER = LoggerFactory.getLogger(Version3PacketParser.class);
	
	private static final Oid AUTH_ERROR_OID = new Oid("1.3.6.1.6.3.15.1.1");
	
	private final int requestId;
	private final int errorStatus;
	private final int errorIndex;
	private final List<Result> results = new LinkedList<Result>();

	public Version3PacketParser(AuthRemoteEngine authEngine, ByteBuffer buffer) throws IOException {
		BerReader ber = new BerReader(buffer);
		ber.beginReadSequence();
		{
			int version = ber.readInteger();
			if (version != BerConstants.VERSION_3) {
				throw new IOException("Invalid version: " + version + " should be " + BerConstants.VERSION_3);
			}
			
			byte securityFlags;

			ber.beginReadSequence();
			{
				ber.readInteger(); // Packet number
				ber.readInteger(); // Max packet size
				securityFlags = ber.readBytes().get();
				int securityModel = ber.readInteger();
				if (securityModel != BerConstants.VERSION_3_USM_SECURITY_MODEL) {
					throw new IOException("Invalid security model: " + securityModel + " should be " + BerConstants.VERSION_3_USM_SECURITY_MODEL);
				}
			}
			ber.endReadSequence();

			BerReader secBer = new BerReader(ber.readBytes());
			secBer.beginReadSequence();
			{
				ByteBuffer engine = secBer.readBytes();
				byte[] id = new byte[engine.remaining()];
				engine.get(id);
				authEngine.setId(id);
				authEngine.setBootCount(secBer.readInteger());
				authEngine.resetTime(secBer.readInteger());
				String login = BerPacketUtils.string(secBer.readBytes());
				if (((securityFlags & BerConstants.VERSION_3_AUTH_FLAG) != 0) && !login.equals(authEngine.getAuthLogin())) {
					throw new IOException("Bad login: " + login + " should be: " + authEngine.getAuthLogin());
				}
				secBer.readBytes();
				ByteBuffer decryptParams = secBer.readBytes();
				if (decryptParams.hasRemaining()) {
					byte[] dp = new byte[decryptParams.remaining()];
					decryptParams.get(dp);
					authEngine.setEncryptionParameters(dp);
				}
			}
			secBer.endReadSequence();

			BerReader pdu;
			if ((securityFlags & BerConstants.VERSION_3_PRIV_FLAG) != 0) {
				pdu = new BerReader(authEngine.decrypt(ber.readBytes()));
			} else {
				pdu = ber;
			}

			pdu.beginReadSequence();
			pdu.readBytes();
			pdu.readBytes();
			
			if (!authEngine.isReady()) {
				LOGGER.trace("Engine not ready");
				if (ber.beginReadSequence() != BerConstants.REPORT) {
					throw new IOException("Not a report packet");
				}
				{
					requestId = pdu.readInteger();
					LOGGER.trace("REPORT <- requestId = {}", requestId);
					{
						int errorStatus = pdu.readInteger();
						int errorIndex = pdu.readInteger();
						LOGGER.trace("REPORT error = {}/{}", errorStatus, errorIndex);
					}

					errorStatus = BerConstants.ERROR_STATUS_RETRY;
					errorIndex = 0;
					LOGGER.trace("REPORT error replaced = {}/{}", errorStatus, errorIndex);
	
					pdu.beginReadSequence();
					{
						while (pdu.hasRemainingInSequence()) {
							pdu.beginReadSequence();
							{
								Oid oid = pdu.readOid();
								String value = pdu.readValue();
								// pdu.readOidValue();
								LOGGER.trace("<- {} = {}", oid, value);
							}
							pdu.endReadSequence();
						}
					}
					pdu.endReadSequence();
				}
				pdu.endReadSequence();
			} else {
				int s = pdu.beginReadSequence();
				if (s == BerConstants.REPORT) {
					requestId = pdu.readInteger();
					LOGGER.trace("REPORT <- requestId = {}", requestId);
					int errorStatus = pdu.readInteger();
					int errorIndex = pdu.readInteger();
					LOGGER.trace("REPORT error = {}/{}", errorStatus, errorIndex);

					pdu.beginReadSequence();
					{
						while (pdu.hasRemainingInSequence()) {
							pdu.beginReadSequence();
							{
								Oid oid = pdu.readOid();
								String value = pdu.readValue();
								LOGGER.trace("<- {} = {}", oid, value);
								if (AUTH_ERROR_OID.isPrefixOf(oid)) {
									LOGGER.error("Authentication failed ({}), requestId = {}", oid, requestId);
									errorStatus = BerConstants.ERROR_STATUS_AUTHENTICATION_FAILED;
									errorIndex = 0;
								}
								// OidValue value = pdu.readOidValue();
								results.add(new Result(oid, value));
							}
							pdu.endReadSequence();
						}
					}
					pdu.endReadSequence();
					
					this.errorStatus = errorStatus;
					this.errorIndex = errorIndex;
				} else {
					if (s != BerConstants.RESPONSE) {
						throw new IOException("Not a response packet");
					}
					requestId = pdu.readInteger();
					LOGGER.trace("RESPONSE <- requestId = {}", requestId);
					errorStatus = pdu.readInteger();
					errorIndex = pdu.readInteger();
					LOGGER.trace("RESPONSE error = {}/{}", errorStatus, errorIndex);

					pdu.beginReadSequence();
					{
						while (pdu.hasRemainingInSequence()) {
							pdu.beginReadSequence();
							{
								Oid oid = pdu.readOid();
								String value = pdu.readValue();
								LOGGER.trace("<- {} = {}", oid, value);
								// OidValue value = pdu.readOidValue();
								results.add(new Result(oid, value));
							}
							pdu.endReadSequence();
						}
					}
					pdu.endReadSequence();
				}
				pdu.endReadSequence();
			}
			pdu.endReadSequence();
		}
		ber.endReadSequence();
		
		authEngine.setReady();
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
