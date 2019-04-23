package com.davfx.ninio.snmp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SnmpPacketParser {

	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpPacketParser.class);
	
	private static final Oid AUTH_ERROR_OID_PREFIX = new Oid("1.3.6.1.6.3.15.1.1");
	private static final Oid AUTH_ERROR_UNKNOWN_ENGINE_ID_OID = new Oid("1.3.6.1.6.3.15.1.1.4.0");
	private static final Oid AUTH_ERROR_NOT_IN_TIME_WINDOW_OID = new Oid("1.3.6.1.6.3.15.1.1.2.0");
	
	private final int requestId;
	private final int errorStatus;
	private final int errorIndex;
	private final List<SnmpResult> results = new LinkedList<SnmpResult>();

	public SnmpPacketParser(AuthRemoteEngine authEngine, ByteBuffer buffer) throws IOException {
		BerReader ber = new BerReader(buffer);
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
								} else {
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
				
				if (authEngine == null) {
					throw new IOException("Response received on invalid version: " + version + " should be " + BerConstants.VERSION_2C);
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
					ByteBuffer decrypted = authEngine.decrypt(ber.readBytes());
					if (decrypted == null) {
						throw new IOException("Could not decrypt, auth engine keys/algorithms not provided");
					}
					pdu = new BerReader(decrypted);
				} else {
					pdu = ber;
				}
	
				pdu.beginReadSequence();
				pdu.readBytes();
				pdu.readBytes();
	
				int requestId;
				int errorStatus;
				int errorIndex;
	
				int s = pdu.beginReadSequence();
				if (s == BerConstants.REPORT) {
					requestId = pdu.readInteger();
					LOGGER.trace("REPORT <- requestId = {}", requestId);
					errorStatus = pdu.readInteger();
					errorIndex = pdu.readInteger();
					LOGGER.trace("REPORT error = {}/{}", errorStatus, errorIndex);
					
					if (errorStatus == 0) {
						errorStatus = BerConstants.ERROR_STATUS_UNKNOWN;
						errorIndex = 0;
					}
	
					pdu.beginReadSequence();
					{
						while (pdu.hasRemainingInSequence()) {
							pdu.beginReadSequence();
							{
								Oid oid = pdu.readOid();
								String value = pdu.readValue();
								LOGGER.trace("<- {} = {}", oid, value);
								
								
								if (AUTH_ERROR_UNKNOWN_ENGINE_ID_OID.isPrefixOf(oid)) {
									LOGGER.trace("Engine not known ({}), requestId = {}", oid, requestId);
									errorStatus = BerConstants.ERROR_STATUS_AUTHENTICATION_NOT_SYNCED;
									errorIndex = 0;
								} else if (AUTH_ERROR_NOT_IN_TIME_WINDOW_OID.isPrefixOf(oid)) {
									LOGGER.trace("Engine not synced ({}), requestId = {}", oid, requestId);
									errorStatus = BerConstants.ERROR_STATUS_AUTHENTICATION_NOT_SYNCED;
									errorIndex = 0;
								} else if (AUTH_ERROR_OID_PREFIX.isPrefixOf(oid)) {
									LOGGER.error("Authentication failed ({}), requestId = {}", oid, requestId);
									errorStatus = BerConstants.ERROR_STATUS_AUTHENTICATION_FAILED;
									errorIndex = 0;
								}
							}
							pdu.endReadSequence();
						}
					}
					pdu.endReadSequence();
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
	
				this.requestId = requestId;
				this.errorStatus = errorStatus;
				this.errorIndex = errorIndex;
		
			} else {
				throw new IOException("Invalid version: " + version + " should be " + BerConstants.VERSION_2C + " or " + BerConstants.VERSION_3);
			}
			
		}
		
		ber.endReadSequence();
	}

	public Iterable<SnmpResult> getResults() {
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
