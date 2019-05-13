package com.davfx.ninio.snmp;

import java.nio.ByteBuffer;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.BaseEncoding;

final class AuthRemoteEngine {
	private static final Logger LOGGER = LoggerFactory.getLogger(AuthRemoteEngine.class);

	private static final int INITIAL_PACKET_NUMBER = 61_473_655; // Let's do as snmpwalk is doing
	
	private int bootCount = 0;
	private int resetTime = 0;
	private byte[] id = null;

	public final AuthRemoteSpecification authRemoteSpecification;
	
	private int packetNumber = INITIAL_PACKET_NUMBER;
    private byte[] encryptionParameters = new byte[8];
    
    private long timeResetAt = 0L;
    private int time = 0;
    
    //%%% private boolean ready = false;
    
    public final EncryptionEngine encryptionEngine;
    private byte[] authKey;
    private byte[] privKey;
    
	public AuthRemoteEngine(AuthRemoteSpecification authRemoteSpecification, EncryptionEngine encryptionEngine) {
		this.authRemoteSpecification = authRemoteSpecification;
		this.encryptionEngine = encryptionEngine;

		authKey = encryptionEngine.regenerateKey(null, authRemoteSpecification.authPassword);
		privKey = encryptionEngine.regenerateKey(null, authRemoteSpecification.privPassword);
	}
	
	public static boolean hasChanged(AuthRemoteSpecification s, AuthRemoteSpecification other) {
		return !Objects.equals(s.authPassword, other.authPassword)
			|| !Objects.equals(s.authDigestAlgorithm, other.authDigestAlgorithm)
			|| !Objects.equals(s.privPassword, other.privPassword)
			|| !Objects.equals(s.privEncryptionAlgorithm, other.privEncryptionAlgorithm);
	}
	public static final class EncryptionEngineKey {
		public final String authDigestAlgorithm;
		public final String privEncryptionAlgorithm;
		
		public EncryptionEngineKey(String authDigestAlgorithm, String privEncryptionAlgorithm) {
			this.authDigestAlgorithm = authDigestAlgorithm;
			this.privEncryptionAlgorithm = privEncryptionAlgorithm;
		}

		@Override
		public int hashCode() {
			return Objects.hash(authDigestAlgorithm, privEncryptionAlgorithm);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (!(obj instanceof EncryptionEngineKey)) {
				return false;
			}
			EncryptionEngineKey other = (EncryptionEngineKey) obj;
			return Objects.equals(authDigestAlgorithm, other.authDigestAlgorithm)
				&& Objects.equals(privEncryptionAlgorithm, other.privEncryptionAlgorithm);
		}
	}
	
	public int incPacketNumber() {
		int n = packetNumber;
		packetNumber++;
		return n;
	}
	
	public byte[] getEncryptionParameters() {
		return encryptionParameters;
	}
	
	public boolean isValid() {
		return (id != null) && (time > 0);
	}

	public byte[] getId() {
		return id;
	}

	public void setId(byte[] id) {
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Auth engine ID: {} -> {}", (this.id == null) ? null : BaseEncoding.base16().encode(this.id), BaseEncoding.base16().encode(id));
		}
		this.id = id;
		authKey = encryptionEngine.regenerateKey(this.id, authRemoteSpecification.authPassword);
		privKey = encryptionEngine.regenerateKey(this.id, authRemoteSpecification.privPassword);
	}
	public void setEncryptionParameters(byte[] encryptionParameters) {
		this.encryptionParameters = encryptionParameters;
	}

	public int getBootCount() {
		return bootCount;
	}

	public void setBootCount(int bootCount) {
		LOGGER.trace("Auth engine boot count: {} -> {}", this.bootCount, bootCount);
		this.bootCount = bootCount;
	}

	public int getTime() {
		return time;
	}

	public void renewTime() {
		if (timeResetAt > 0L) {
			int oldTime = time;
			time = resetTime + ((int) ((System.currentTimeMillis() - timeResetAt) / 1000L));
			LOGGER.trace("Auth engine time: ({}) {} -> {}", resetTime, oldTime, time);
		}
	}
	public void resetTime(int resetTime) {
		LOGGER.trace("Auth engine reset time: {} ({}) -> {}", this.resetTime, time, resetTime);
		if (resetTime == 0) {
			timeResetAt = 0L;
			time = 0;
			this.resetTime = 0;
		} else {
			timeResetAt = System.currentTimeMillis();
			this.resetTime = resetTime;
			time = resetTime;
		}
	}

	public byte[] hash(ByteBuffer message) {
		return encryptionEngine.hash(authKey, message);
	}

	public ByteBuffer encrypt(ByteBuffer decryptedBuffer) {
		return encryptionEngine.encrypt(getBootCount(), getTime(), encryptionParameters, privKey, decryptedBuffer);
	}

	public ByteBuffer decrypt(ByteBuffer encryptedBuffer) {
		return encryptionEngine.decrypt(getBootCount(), getTime(), encryptionParameters, privKey, encryptedBuffer);
	}
}
